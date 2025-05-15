package com.erp.aierpbackend.listener;

import com.erp.aierpbackend.config.RabbitMQConfig;
import com.erp.aierpbackend.dto.dynamics.JobLedgerEntryDTO;
import com.erp.aierpbackend.dto.dynamics.JobListDTO; // Import JobListDTO
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.VerificationRequest;
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.VerificationRequestRepository;
import com.erp.aierpbackend.service.ActivityLogService; // Import ActivityLogService
import com.erp.aierpbackend.service.BusinessCentralService;
import com.erp.aierpbackend.service.JobDocumentVerificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data; // Import Data for inner class
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate; // Import LocalDate
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class JobLedgerIngestionListener {

    // Keep NlpService if used elsewhere, otherwise remove
    // private final NlpService nlpService;
    private final BusinessCentralService businessCentralService;
    private final JobDocumentVerificationService jobDocumentVerificationService;
    private final JobRepository jobRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final ActivityLogService activityLogService; // Add ActivityLogService field
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;

    // Inner class to represent the expected message structure
    @Data
    private static class VerificationMessage {
        private String verificationRequestId;
        private String jobNo;
    }

    @Autowired
    public JobLedgerIngestionListener( // Removed extra comma at the end of parameter list if present
            BusinessCentralService businessCentralService,
            JobDocumentVerificationService jobDocumentVerificationService,
            JobRepository jobRepository,
            VerificationRequestRepository verificationRequestRepository,
            ActivityLogService activityLogService,
            MessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.businessCentralService = businessCentralService;
        this.jobDocumentVerificationService = jobDocumentVerificationService;
        this.jobRepository = jobRepository;
        this.verificationRequestRepository = verificationRequestRepository;
        this.activityLogService = activityLogService; // Assign ActivityLogService
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }


    // @RabbitListener(queues = RabbitMQConfig.BC_JOB_LEDGER_QUEUE_NAME) // Disabled for new user-driven flow
    public void handleIncomingMessage(Message message) {
        String messageBody = new String(message.getBody());
        log.debug("Received raw message body: {}", messageBody);
        VerificationMessage verificationMessage = null;

        try {
            // --- Start Deserialization Logic ---
            try {
                // Attempt 1: Deserialize directly as the target object
                verificationMessage = objectMapper.readValue(messageBody, VerificationMessage.class);
            } catch (JsonProcessingException e) {
                log.warn("Direct JSON deserialization failed ({}). Attempting to deserialize as String first...", e.getMessage());
                try {
                    // Attempt 2: Deserialize as String to handle potential double-encoding
                    String innerJson = objectMapper.readValue(messageBody, String.class);
                    log.debug("Successfully deserialized outer string, attempting inner JSON deserialization: {}", innerJson);
                    verificationMessage = objectMapper.readValue(innerJson, VerificationMessage.class);
                } catch (JsonProcessingException e2) {
                    // If both attempts fail, log and re-throw or handle as unparseable
                    log.error("Failed to deserialize incoming message after attempting double-decode. Body: '{}'. Error: {}", messageBody, e2.getMessage(), e2);
                    throw e2; // Re-throw to be caught by the outer catch block
                }
            }
            // --- End Deserialization Logic ---

            // Proceed if deserialization was successful
            if (verificationMessage != null) {
                 if (verificationMessage.getVerificationRequestId() == null || verificationMessage.getJobNo() == null) {
                     log.error("Deserialized message has missing verificationRequestId or jobNo: {}", verificationMessage);
                     // Consider DLQ
                     return; // Stop processing this message
                 } // Added missing closing brace

                log.info("Received verification request message: RequestId={}, JobNo={}",
                        verificationMessage.getVerificationRequestId(), verificationMessage.getJobNo());

                // Call the transactional processing method
                processVerificationRequest(verificationMessage.getVerificationRequestId(), verificationMessage.getJobNo());

            } else {
                 // This case should ideally not be reached if exceptions are thrown correctly above
                 log.error("VerificationMessage is null after deserialization attempts for body: {}", messageBody);
            }

        } catch (JsonProcessingException e) { // Catch deserialization errors from inner/outer try
            log.error("Failed to deserialize incoming JSON message. Body: '{}'. Error: {}", messageBody, e.getMessage(), e);
            // Consider DLQ for unparseable messages
        } catch (Exception e) { // Catch any other unexpected errors during handling
             log.error("Unhandled exception during handleIncomingMessage for message body: '{}'. Error: {}", messageBody, e.getMessage(), e);
             // Consider DLQ
        }
    }

    // Removed processJobLedgerEntry as it's no longer the primary flow handled here

    @Transactional // Ensure database operations are atomic for a single request
    protected void processVerificationRequest(String verificationRequestId, String jobNo) {
        log.debug("Processing verification request: RequestId={}, JobNo={}", verificationRequestId, jobNo);
        // Log processing start
        // Using EVENT_JOB_PROCESSED for now, maybe add specific later like EVENT_VERIFICATION_STARTED
        activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                String.format("Verification [ID: %s] processing started for Job No: %s", verificationRequestId, jobNo),
                null, // Or associate with Job ID if available early? Job object not fetched yet.
                "System");


        // 1. Find the VerificationRequest
        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId)
                .orElse(null);

        if (request == null) {
            log.error("VerificationRequest not found for ID: {}. Cannot process.", verificationRequestId);
            // This shouldn't happen if the controller creates it first, but handle defensively.
            return;
        }

        // Check if already processed to prevent reprocessing (optional idempotency)
        if (request.getStatus() != VerificationRequest.VerificationStatus.PENDING) {
             log.warn("VerificationRequest ID: {} already processed or is processing (Status: {}). Skipping.", verificationRequestId, request.getStatus());
             return;
        }


        // 2. Find the associated Job
        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} associated with RequestId: {}. Cannot process.", jobNo, verificationRequestId);
            request.setStatus(VerificationRequest.VerificationStatus.FAILED);
            request.setResultTimestamp(LocalDateTime.now());
            request.setDiscrepanciesJson(serializeDiscrepancies(List.of("Associated Job record not found in database.")));
            verificationRequestRepository.save(request);
            // Log specific error
            activityLogService.recordActivity(ActivityLogService.EVENT_ERROR,
                    String.format("Verification [ID: %s] failed for Job No: %s. Reason: Associated Job record not found.", verificationRequestId, jobNo),
                    null, // No Job ID available here
                    "System");
            return;
        }

        // 3. Update statuses to PROCESSING
        request.setStatus(VerificationRequest.VerificationStatus.PROCESSING);
        verificationRequestRepository.save(request); // Save processing status

        job.setStatus(Job.JobStatus.PROCESSING);
        job.setLastProcessedAt(LocalDateTime.now());
        jobRepository.save(job); // Save processing status

        // Final references for reactive chain
        final VerificationRequest currentRequest = request;
        final Job currentJob = job;
        final String finalVerificationRequestId = verificationRequestId; // Final variable for lambda

        // 4. Qualification Check using Job_List
        businessCentralService.fetchJobListEntry(jobNo)
            .flatMap(jobListEntry -> { // jobListEntry is available here
                // Check qualification criteria
                boolean isFirstCheckDone = jobListEntry.getFirstCheckDate() != null && !jobListEntry.getFirstCheckDate().isBlank();
                boolean isSecondCheckPending = jobListEntry.getSecondCheckDate() == null || jobListEntry.getSecondCheckDate().isBlank();

                if (isFirstCheckDone && isSecondCheckPending) {
                    log.info("Job No: {} qualifies for second check (1st check date: {}, 2nd check date: {}). Proceeding.",
                             jobNo, jobListEntry.getFirstCheckDate(), jobListEntry.getSecondCheckDate());
                    // 5. Fetch Ledger Entry (only if qualified)
                    return businessCentralService.fetchJobLedgerEntries(jobNo).next(); // Fetch the first ledger entry
                } else {
                    log.warn("Job No: {} does not qualify for second check. Skipping verification. (1st check date: {}, 2nd check date: {})",
                             jobNo, jobListEntry.getFirstCheckDate(), jobListEntry.getSecondCheckDate());
                    // Update status to SKIPPED/NOT_QUALIFIED
                    currentRequest.setStatus(VerificationRequest.VerificationStatus.SKIPPED); // Assuming SKIPPED status exists
                    currentRequest.setResultTimestamp(LocalDateTime.now());
                    currentRequest.setDiscrepanciesJson(serializeDiscrepancies(List.of("Job does not qualify for second check.")));
                    // Use final variables inside lambda
                    currentJob.setStatus(Job.JobStatus.SKIPPED); // Assuming SKIPPED status exists
                    verificationRequestRepository.save(currentRequest);
                    jobRepository.save(currentJob);
                    activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                            String.format("Verification [ID: %s] skipped for Job No: %s. Reason: Does not qualify for second check.", finalVerificationRequestId, jobNo),
                            currentJob.getId(), "System");
                    return Mono.empty(); // Stop the chain for this job
                }
            })
            // Chain to fetch ledger entry only if qualification passed
            .flatMap(ledgerEntry -> { // ledgerEntry is available in this scope
                log.info("Found Job Ledger Entry No: {} for qualified Job No: {}. Proceeding with document verification.", ledgerEntry.getEntryNo(), jobNo);
                // Wrap verification call in Mono to handle exceptions reactively
                return Mono.fromCallable(() -> jobDocumentVerificationService.verifyJobDocuments(ledgerEntry, jobNo))
                    .flatMap(discrepancyMessages -> { // discrepancyMessages available here
                        // 6. Update VerificationRequest and Job based on results
                        currentRequest.setResultTimestamp(LocalDateTime.now());
                        currentRequest.setDiscrepanciesJson(serializeDiscrepancies(discrepancyMessages));

                        if (discrepancyMessages.isEmpty()) {
                            log.info("SUCCESS: Document verification passed for Job No: {}", jobNo);
                            currentRequest.setStatus(VerificationRequest.VerificationStatus.COMPLETED);
                            currentJob.setStatus(Job.JobStatus.VERIFIED);
                            activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                                    String.format("Verification [ID: %s] successful for Job No: %s", finalVerificationRequestId, jobNo),
                                    currentJob.getId(), "System");
                            // Update BC Job Card - Handled within verification service now
                        } else {
                            log.warn("FAILURE: Document verification failed for Job No: {} with {} discrepancies:", jobNo, discrepancyMessages.size());
                            discrepancyMessages.forEach(d -> log.warn("- {}", d));
                            currentRequest.setStatus(VerificationRequest.VerificationStatus.COMPLETED); // Completed, but with issues
                            currentJob.setStatus(Job.JobStatus.FLAGGED);
                            activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                                    String.format("Verification [ID: %s] flagged for Job No: %s. Discrepancies: %d", finalVerificationRequestId, jobNo, discrepancyMessages.size()),
                                    currentJob.getId(), "System");
                        }
                        // Save updated entities
                        verificationRequestRepository.save(currentRequest);
                        jobRepository.save(currentJob);
                        return Mono.just(true); // Signal success
                    })
                    .onErrorResume(e -> { // Handle exceptions specifically from verifyJobDocuments call
                        log.error("Exception during jobDocumentVerificationService call for Job No: {}", jobNo, e);
                        currentRequest.setStatus(VerificationRequest.VerificationStatus.FAILED);
                        currentRequest.setResultTimestamp(LocalDateTime.now());
                        String errorMsg = "Error during verification: " + e.getMessage();
                        currentRequest.setDiscrepanciesJson(serializeDiscrepancies(List.of(errorMsg)));
                        currentJob.setStatus(Job.JobStatus.ERROR);
                        verificationRequestRepository.save(currentRequest);
                        jobRepository.save(currentJob);
                        activityLogService.recordActivity(ActivityLogService.EVENT_ERROR,
                                String.format("Verification [ID: %s] error for Job No: %s. Reason: %s", finalVerificationRequestId, jobNo, e.getMessage()),
                                currentJob.getId(), "System");
                        return Mono.error(e); // Propagate error to main chain's error handling
                    });
            }) // End of flatMap(ledgerEntry -> ...)
            .switchIfEmpty(Mono.defer(() -> { // Handles cases where qualification check returned Mono.empty() OR fetchLedgerEntries returned empty
                // Check if status is still PROCESSING, implying ledger entry wasn't found after qualification
                if (currentRequest.getStatus() == VerificationRequest.VerificationStatus.PROCESSING) {
                    log.error("No Job Ledger Entry found for qualified Job No: {}. Cannot perform verification.", jobNo);
                    currentRequest.setStatus(VerificationRequest.VerificationStatus.FAILED);
                    currentRequest.setResultTimestamp(LocalDateTime.now());
                    // Use a specific message for ledger not found
                    currentRequest.setDiscrepanciesJson(serializeDiscrepancies(List.of("Ledger entry not found in Business Central for qualified job.")));
                    currentJob.setStatus(Job.JobStatus.ERROR);
                    verificationRequestRepository.save(currentRequest);
                    jobRepository.save(currentJob);
                    activityLogService.recordActivity(ActivityLogService.EVENT_ERROR,
                            String.format("Verification [ID: %s] failed for Job No: %s. Reason: Ledger entry not found.", finalVerificationRequestId, jobNo),
                            currentJob.getId(), "System");
                } else {
                    // This means qualification check failed or job was already skipped/failed before ledger fetch attempt
                    log.debug("switchIfEmpty entered for RequestId: {}, but status was already {}. No action taken.", finalVerificationRequestId, currentRequest.getStatus());
                }
                return Mono.empty(); // Complete without success signal if ledger not found or skipped
            }))
            // Consolidated error handling for the entire reactive chain
            .doOnError(error -> {
                log.error("Error occurred in verification pipeline for RequestId: {}. Final status check.", finalVerificationRequestId, error);
                // Ensure final status reflects error if not already set appropriately
                VerificationRequest finalRequestState = verificationRequestRepository.findById(finalVerificationRequestId).orElse(currentRequest); // Re-fetch for latest state
                Job finalJobState = jobRepository.findById(currentJob.getId()).orElse(currentJob);

                if (finalRequestState.getStatus() != VerificationRequest.VerificationStatus.FAILED && finalRequestState.getStatus() != VerificationRequest.VerificationStatus.SKIPPED) {
                    finalRequestState.setStatus(VerificationRequest.VerificationStatus.FAILED);
                    finalRequestState.setResultTimestamp(LocalDateTime.now());
                    finalRequestState.setDiscrepanciesJson(serializeDiscrepancies(List.of("Pipeline error: " + error.getMessage())));
                    verificationRequestRepository.save(finalRequestState);
                }
                 if (finalJobState.getStatus() != Job.JobStatus.ERROR && finalJobState.getStatus() != Job.JobStatus.SKIPPED) {
                     finalJobState.setStatus(Job.JobStatus.ERROR);
                     jobRepository.save(finalJobState);
                     // Log only if status was changed here
                     activityLogService.recordActivity(ActivityLogService.EVENT_ERROR,
                             String.format("Verification [ID: %s] pipeline error for Job No: %s. Reason: %s", finalVerificationRequestId, jobNo, error.getMessage()),
                             finalJobState.getId(), "System");
                 }
            })
            .subscribe(
                success -> log.debug("Reactive pipeline processing completed (may include skips) for RequestId: {}", finalVerificationRequestId),
                error -> log.error("Reactive pipeline failed definitively after error handling for RequestId: {}", finalVerificationRequestId) // Error already logged by doOnError
            );

        log.debug("Verification processing pipeline initiated for RequestId: {}", finalVerificationRequestId);
    }

    // Helper to serialize discrepancy list to JSON
    private String serializeDiscrepancies(List<String> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return null; // Or return "[]"
        }
        try {
            return objectMapper.writeValueAsString(discrepancies);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize discrepancies list to JSON", e);
            // Return a fallback representation or null
            return "[\"Error serializing discrepancies\"]";
        }
    }

    // Removed triggerVerificationProcess method if not used elsewhere

    /* Removed original handleJobLedgerEntry method */
}
