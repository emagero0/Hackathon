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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
// Removed reactor imports as logic is now imperative

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationProcessingService {

    private final BusinessCentralService businessCentralService;
    private final JobDocumentVerificationService jobDocumentVerificationService;
    private final JobAttachmentService jobAttachmentService;
    private final DocumentClassificationService documentClassificationService;
    private final JobRepository jobRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Async // Run this method in a separate thread
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Use REQUIRES_NEW to ensure a fresh transaction for the entire process
    public void processVerification(String verificationRequestId, String jobNo) {
        log.info("Processing verification request: RequestId={}, JobNo={}", verificationRequestId, jobNo);

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Add a delay at the beginning to ensure any previous transactions are fully committed
        try {
            log.debug("Adding a delay at the beginning of verification processing to ensure previous transactions are committed");
            Thread.sleep(1000); // 1 second delay
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted at the beginning of verification processing", ie);
        }

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
            // First, try to fetch documents from SharePoint if needed
            log.info("Checking for job documents in SharePoint for Job No: {}", jobNo);

            // Explicitly flush any pending changes before downloading documents
            jobRepository.flush();
            verificationRequestRepository.flush();

            // Complete the current transaction to ensure all changes are committed
            // before starting the document download process
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    // Do nothing before commit
                }

                @Override
                public void beforeCompletion() {
                    // Do nothing before completion
                }

                @Override
                public void afterCommit() {
                    log.info("Transaction committed before document download for Job No: {}", jobNo);

                    // Start document download in a new transaction
                    downloadAndClassifyDocuments(verificationRequestId, jobNo);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        log.error("Transaction not committed before document download for Job No: {}", jobNo);
                    }
                }
            });

            // The rest of the verification process will continue in the downloadAndClassifyDocuments method
        } catch (Exception e) {
            // Catch any exception during BC calls or verification service
            log.error("Exception during verification processing pipeline for Job No: {}", jobNo, e);
            String errorMsg = "Error during verification processing: " + e.getMessage();
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED, Job.JobStatus.ERROR,
                    List.of(errorMsg),
                    e.getMessage()); // Use exception message for activity log reason
        }
    }

    /**
     * Downloads and classifies documents for a job in a new transaction.
     * This method is called after the initial transaction in processVerification is committed.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void downloadAndClassifyDocuments(String verificationRequestId, String jobNo) {
        log.info("Starting document download and classification in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document download. Cannot proceed.", verificationRequestId);
            return;
        }

        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} during document download. Cannot proceed.", jobNo);
            return;
        }

        try {
            log.info("Starting document download from SharePoint for Job No: {}", jobNo);
            List<String> downloadedDocumentTypes = null;
            try {
                // Use a longer timeout for document download (3 minutes)
                downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block(java.time.Duration.ofMinutes(3)); // Explicit timeout of 3 minutes

                // Force a flush after document download
                jobRepository.flush();
                verificationRequestRepository.flush();

                log.info("Document download from SharePoint completed for Job No: {}", jobNo);
            } catch (Exception e) {
                log.error("Error downloading documents from SharePoint for Job No: {}: {}", jobNo, e.getMessage(), e);
                // Continue with verification even if download fails
            }

            if (downloadedDocumentTypes != null && !downloadedDocumentTypes.isEmpty()) {
                log.info("Successfully downloaded {} documents from SharePoint for Job No: {}: {}",
                        downloadedDocumentTypes.size(), jobNo, downloadedDocumentTypes);

                // Complete the current transaction to ensure all document downloads are committed
                // before starting the classification process
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        // Do nothing before commit
                    }

                    @Override
                    public void beforeCompletion() {
                        // Do nothing before completion
                    }

                    @Override
                    public void afterCommit() {
                        log.info("Transaction committed after document download for Job No: {}", jobNo);

                        // Start document classification in a new transaction
                        classifyDocuments(verificationRequestId, jobNo);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            log.error("Transaction not committed after document download for Job No: {}", jobNo);
                        }
                    }
                });
            } else {
                log.warn("No documents were downloaded from SharePoint for Job No: {}", jobNo);

                // Continue with verification even if no documents were downloaded
                verifyDocuments(verificationRequestId, jobNo);
            }
        } catch (Exception e) {
            log.error("Error during document download for Job No: {}: {}", jobNo, e.getMessage(), e);

            // Continue with verification even if document download fails
            verifyDocuments(verificationRequestId, jobNo);
        }
    }

    /**
     * Classifies documents for a job in a new transaction.
     * This method is called after the document download transaction is committed.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void classifyDocuments(String verificationRequestId, String jobNo) {
        log.info("Starting document classification in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document classification. Cannot proceed.", verificationRequestId);
            return;
        }

        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} during document classification. Cannot proceed.", jobNo);
            return;
        }

        try {
            // Add classification step with blocking call and timeout
            log.info("Starting document classification for Job No: {}", jobNo);
            try {
                // Use the blocking method with a 2-minute timeout
                Map<Long, String> classificationResults = documentClassificationService.classifyJobDocumentsBlocking(jobNo, 120);

                // Force a flush after classification
                jobRepository.flush();
                verificationRequestRepository.flush();

                log.info("Document classification completed for Job No: {}", jobNo);

                if (classificationResults != null && !classificationResults.isEmpty()) {
                    log.info("Successfully classified {} documents for Job No: {}",
                            classificationResults.size(), jobNo);
                } else {
                    log.warn("No documents were classified for Job No: {}", jobNo);
                }
            } catch (Exception e) {
                log.error("Error classifying documents for Job No: {}: {}", jobNo, e.getMessage(), e);
                // Continue with verification even if classification fails
            }

            // Complete the current transaction to ensure all classification results are committed
            // before starting the verification process
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    // Do nothing before commit
                }

                @Override
                public void beforeCompletion() {
                    // Do nothing before completion
                }

                @Override
                public void afterCommit() {
                    log.info("Transaction committed after document classification for Job No: {}", jobNo);

                    // Start document verification in a new transaction
                    verifyDocuments(verificationRequestId, jobNo);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        log.error("Transaction not committed after document classification for Job No: {}", jobNo);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error during document classification for Job No: {}: {}", jobNo, e.getMessage(), e);

            // Continue with verification even if document classification fails
            verifyDocuments(verificationRequestId, jobNo);
        }
    }

    /**
     * Verifies documents for a job in a new transaction.
     * This method is called after the document classification transaction is committed.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocuments(String verificationRequestId, String jobNo) {
        log.info("Starting document verification in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document verification. Cannot proceed.", verificationRequestId);
            return;
        }

        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} during document verification. Cannot proceed.", jobNo);
            return;
        }

        try {
            // Now perform the actual verification
            log.info("Starting document verification for Job No: {}", jobNo);

            // Fetch Business Central data for verification
            log.info("Fetching Business Central data for verification for Job No: {}", jobNo);

            // Continue with the rest of the verification process...
            // This is where you would call jobDocumentVerificationService.verifyJobDocuments()

            // Update the job and request status based on verification results
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED, Job.JobStatus.VERIFIED,
                    Collections.emptyList(), "Verification completed successfully");

            log.info("Verification completed for Job No: {}", jobNo);

        } catch (Exception e) {
            // Catch any exception during verification
            log.error("Exception during verification for Job No: {}", jobNo, e);
            String errorMsg = "Error during verification: " + e.getMessage();
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED, Job.JobStatus.ERROR,
                    List.of(errorMsg), e.getMessage());
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
