package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.dynamics.JobLedgerEntryDTO;
// Removed JobListDTO import as eligibility check is removed here
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.VerificationRequest;
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.VerificationRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Removed reactor imports as logic is now imperative

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationProcessingService {

    private final BusinessCentralService businessCentralService;
    private final JobDocumentVerificationService jobDocumentVerificationService;
    private final JobRepository jobRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Async // Run this method in a separate thread
    @Transactional // Ensure database operations are atomic for a single request
    public void processVerification(String verificationRequestId, String jobNo) {
        log.debug("Processing verification request: RequestId={}, JobNo={}", verificationRequestId, jobNo);
        activityLogService.recordActivity(ActivityLogService.EVENT_JOB_PROCESSED,
                String.format("Verification [ID: %s] processing started for Job No: %s", verificationRequestId, jobNo),
                null, "System");

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {}. Cannot process.", verificationRequestId);
            // Cannot log activity without request/job context easily here
            return;
        }

        // Check if already processed to prevent reprocessing (idempotency)
        if (request.getStatus() != VerificationRequest.VerificationStatus.PENDING) {
            log.warn("VerificationRequest ID: {} already processed or is processing (Status: {}). Skipping.", verificationRequestId, request.getStatus());
            return;
        }

        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} associated with RequestId: {}. Cannot process.", jobNo, verificationRequestId);
            updateRequestAndJobStatus(request, null, VerificationRequest.VerificationStatus.FAILED, null,
                    List.of("Associated Job record not found in database."),
                    "Associated Job record not found.");
            return;
        }

        // Update statuses to PROCESSING
        updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.PROCESSING, Job.JobStatus.PROCESSING, null, null);
        log.debug("Set status to PROCESSING for RequestId: {}, JobNo: {}", verificationRequestId, jobNo);

        try {
            // Fetch Ledger Entry - Assuming eligibility was checked before triggering
            // Use blockFirst() to get the first element or null if the Flux is empty
            JobLedgerEntryDTO ledgerEntry = businessCentralService.fetchJobLedgerEntries(jobNo).blockFirst(); // Blocking call

            if (ledgerEntry == null) {
                log.error("No Job Ledger Entry found for Job No: {}. Cannot perform verification.", jobNo);
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED, Job.JobStatus.ERROR,
                        List.of("Ledger entry not found in Business Central for job."),
                        "Ledger entry not found.");
                return;
            }

            log.info("Found Job Ledger Entry No: {} for Job No: {}. Proceeding with document verification.", ledgerEntry.getEntryNo(), jobNo);

            // Perform verification (blocking call within try-catch)
            List<String> discrepancyMessages = jobDocumentVerificationService.verifyJobDocuments(ledgerEntry, jobNo);

            // Update based on results
            if (discrepancyMessages.isEmpty()) {
                log.info("SUCCESS: Document verification passed for Job No: {}", jobNo);
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED, Job.JobStatus.VERIFIED,
                        Collections.emptyList(), // Explicitly empty list for success
                        "Verification successful.");
                // BC Update is handled within verifyJobDocuments now
            } else {
                log.warn("FAILURE: Document verification failed for Job No: {} with {} discrepancies:", jobNo, discrepancyMessages.size());
                discrepancyMessages.forEach(d -> log.warn("- {}", d));
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED, Job.JobStatus.FLAGGED,
                        discrepancyMessages,
                        String.format("Verification flagged. Discrepancies: %d", discrepancyMessages.size()));
            }

        } catch (Exception e) {
            // Catch any exception during BC calls or verification service
            log.error("Exception during verification processing pipeline for Job No: {}", jobNo, e);
            String errorMsg = "Error during verification processing: " + e.getMessage();
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED, Job.JobStatus.ERROR,
                    List.of(errorMsg),
                    e.getMessage()); // Use exception message for activity log reason
        }
    }

    // Helper method to update statuses and log activity
    private void updateRequestAndJobStatus(VerificationRequest request, Job job,
                                           VerificationRequest.VerificationStatus requestStatus, Job.JobStatus jobStatus,
                                           List<String> discrepancies, String activityLogReason) {
        try {
            request.setStatus(requestStatus);
            request.setResultTimestamp(LocalDateTime.now());
            request.setDiscrepanciesJson(serializeDiscrepancies(discrepancies));
            verificationRequestRepository.save(request);

            String activityLogEvent = ActivityLogService.EVENT_JOB_PROCESSED; // Default
            Long jobIdForLog = null;

            if (job != null && jobStatus != null) {
                job.setStatus(jobStatus);
                job.setLastProcessedAt(LocalDateTime.now());
                jobRepository.save(job);
                jobIdForLog = job.getId(); // Use job ID if available
            }

            // Determine Activity Log event type based on status
            if (requestStatus == VerificationRequest.VerificationStatus.FAILED || (jobStatus != null && jobStatus == Job.JobStatus.ERROR)) {
                activityLogEvent = ActivityLogService.EVENT_ERROR;
            } else if (jobStatus != null && jobStatus == Job.JobStatus.FLAGGED) {
                 activityLogEvent = ActivityLogService.EVENT_JOB_PROCESSED; // Keep as processed, but reason indicates flagged
            } else if (jobStatus != null && jobStatus == Job.JobStatus.SKIPPED) {
                 activityLogEvent = ActivityLogService.EVENT_JOB_PROCESSED; // Keep as processed, reason indicates skipped
            }


            // Log activity
            if (activityLogReason != null) {
                 String messageFormat = "Verification [ID: %s] %s for Job No: %s. Reason: %s";
                 String statusText = requestStatus.toString().toLowerCase();
                 if (requestStatus == VerificationRequest.VerificationStatus.COMPLETED && jobStatus == Job.JobStatus.VERIFIED) {
                     statusText = "successful";
                     messageFormat = "Verification [ID: %s] %s for Job No: %s"; // Simpler message for success
                     activityLogService.recordActivity(activityLogEvent,
                             String.format(messageFormat, request.getId(), statusText, request.getJobNo()),
                             jobIdForLog, "System");
                 } else if (requestStatus == VerificationRequest.VerificationStatus.COMPLETED && jobStatus == Job.JobStatus.FLAGGED) {
                     statusText = "flagged";
                     activityLogService.recordActivity(activityLogEvent,
                             String.format(messageFormat, request.getId(), statusText, request.getJobNo(), activityLogReason),
                             jobIdForLog, "System");
                 }
                  else {
                     activityLogService.recordActivity(activityLogEvent,
                             String.format(messageFormat, request.getId(), statusText, request.getJobNo(), activityLogReason),
                             jobIdForLog, "System");
                 }
            }

        } catch (Exception e) {
            // Log error during status update itself
            log.error("Failed to update status or log activity for RequestId: {}, JobNo: {}", request.getId(), request.getJobNo(), e);
        }
    }


    // Helper to serialize discrepancy list to JSON
    private String serializeDiscrepancies(List<String> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return "[]"; // Return empty JSON array for no discrepancies
        }
        try {
            return objectMapper.writeValueAsString(discrepancies);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize discrepancies list to JSON", e);
            return "[\"Error serializing discrepancies\"]"; // Fallback JSON
        }
    }
}
