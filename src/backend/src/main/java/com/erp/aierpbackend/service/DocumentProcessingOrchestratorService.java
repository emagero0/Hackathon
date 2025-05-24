package com.erp.aierpbackend.service;

import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.entity.VerificationRequest;
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.VerificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for orchestrating document processing in separate transactions.
 * This service ensures that document download, classification, and verification
 * each run in their own transaction to prevent transaction-related issues.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingOrchestratorService {

    private final JobAttachmentService jobAttachmentService;
    private final DocumentClassificationService documentClassificationService;
    private final JobDocumentVerificationService jobDocumentVerificationService;
    private final BusinessCentralService businessCentralService;
    private final JobDocumentService jobDocumentService;
    private final VerificationRequestRepository verificationRequestRepository;
    private final JobRepository jobRepository;
    private final ActivityLogService activityLogService;

    /**
     * Orchestrates the document download and classification process.
     * This method is called from the afterCommit callback in VerificationProcessingService.
     * It coordinates the entire document processing workflow but ensures each phase completes
     * fully before the next phase begins, avoiding transaction isolation issues.
     *
     * The revised flow ensures all documents are processed and their details (sales quote number,
     * invoice number) are returned before the Business Central checks begin.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    public void orchestrateDocumentProcessing(String verificationRequestId, String jobNo) {
        log.info("Starting document processing orchestration for Job No: {}", jobNo);

        try {
            // Phase 1: Download documents in a new transaction and wait for it to complete
            log.info("Starting Phase 1: Document download for Job No: {}", jobNo);
            downloadDocumentsInNewTransaction(verificationRequestId, jobNo);

            // Add a small delay to ensure the transaction is fully committed
            try {
                log.info("Waiting for download transaction to fully commit for Job No: {}", jobNo);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for download transaction to commit: {}", e.getMessage());
            }

            // Phase 2: Extract document identifiers in a new transaction
            // This step processes all documents and extracts key identifiers before any Business Central checks
            log.info("Starting Phase 2: Document identifier extraction for Job No: {}", jobNo);
            extractDocumentIdentifiersInNewTransaction(verificationRequestId, jobNo);

            // Add a small delay to ensure the transaction is fully committed
            try {
                log.info("Waiting for extraction transaction to fully commit for Job No: {}", jobNo);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for extraction transaction to commit: {}", e.getMessage());
            }

            // Phase 3: Verify documents with the extracted identifiers in a new transaction
            log.info("Starting Phase 3: Document verification for Job No: {}", jobNo);
            verifyDocumentsWithExtractedIdentifiersInNewTransaction(verificationRequestId, jobNo);

        } catch (Exception e) {
            log.error("Error during document processing orchestration for Job No: {}: {}", jobNo, e.getMessage(), e);
            handleProcessingError(verificationRequestId, jobNo, e);
        }
    }

    /**
     * Downloads documents from SharePoint in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void downloadDocumentsInNewTransaction(String verificationRequestId, String jobNo) {
        log.info("Starting document download in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document download. Cannot proceed.", verificationRequestId);
            return;
        }

        try {
            log.info("Starting document download from SharePoint for Job No: {}", jobNo);
            try {
                // Use a longer timeout for document download (3 minutes)
                List<String> downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block(java.time.Duration.ofMinutes(3)); // Explicit timeout of 3 minutes

                if (downloadedDocumentTypes != null && !downloadedDocumentTypes.isEmpty()) {
                    log.info("Successfully downloaded {} documents from SharePoint for Job No: {}: {}",
                            downloadedDocumentTypes.size(), jobNo, downloadedDocumentTypes);
                } else {
                    log.warn("No documents were downloaded from SharePoint for Job No: {}", jobNo);
                }

                log.info("Document download from SharePoint completed for Job No: {}", jobNo);
            } catch (Exception e) {
                log.error("Error downloading documents from SharePoint for Job No: {}: {}", jobNo, e.getMessage(), e);
                // Continue with next steps even if download fails
            }
        } catch (Exception e) {
            log.error("Error during document download for Job No: {}: {}", jobNo, e.getMessage(), e);
            // Log error but don't rethrow - allow process to continue
        }
    }

    /**
     * Legacy method kept for backward compatibility.
     * @deprecated Use downloadDocumentsInNewTransaction instead
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void downloadDocuments(String verificationRequestId, String jobNo) {
        log.warn("Using deprecated downloadDocuments method - should use downloadDocumentsInNewTransaction");
        downloadDocumentsInNewTransaction(verificationRequestId, jobNo);
    }

    /**
     * Classifies documents for a job in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void classifyDocumentsInNewTransaction(String verificationRequestId, String jobNo) {
        log.info("Starting document classification in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document classification. Cannot proceed.", verificationRequestId);
            return;
        }

        try {
            // Add classification step with blocking call and timeout
            log.info("Starting document classification for Job No: {}", jobNo);
            try {
                // Use the blocking method with a 2-minute timeout
                Map<Long, String> classificationResults = documentClassificationService.classifyJobDocumentsBlocking(jobNo, 120);

                log.info("Document classification completed for Job No: {}", jobNo);

                if (classificationResults != null && !classificationResults.isEmpty()) {
                    log.info("Successfully classified {} documents for Job No: {}",
                            classificationResults.size(), jobNo);
                } else {
                    log.warn("No documents were classified for Job No: {}", jobNo);
                }
            } catch (Exception e) {
                log.error("Error classifying documents for Job No: {}: {}", jobNo, e.getMessage(), e);
                // Continue with next steps even if classification fails
            }
        } catch (Exception e) {
            log.error("Error during document classification for Job No: {}: {}", jobNo, e.getMessage(), e);
            // Log error but don't rethrow - allow process to continue
        }
    }

    /**
     * Legacy method kept for backward compatibility.
     * @deprecated Use classifyDocumentsInNewTransaction instead
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void classifyDocuments(String verificationRequestId, String jobNo) {
        log.warn("Using deprecated classifyDocuments method - should use classifyDocumentsInNewTransaction");
        classifyDocumentsInNewTransaction(verificationRequestId, jobNo);
    }

    /**
     * Extracts document identifiers (sales quote number, invoice number) from all documents in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     * It processes all documents and extracts key identifiers before any Business Central checks.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void extractDocumentIdentifiersInNewTransaction(String verificationRequestId, String jobNo) {
        log.info("Starting document identifier extraction in a new transaction for Job No: {}", jobNo);

        VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
        if (request == null) {
            log.error("VerificationRequest not found for ID: {} during document identifier extraction. Cannot proceed.", verificationRequestId);
            return;
        }

        Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);
        if (job == null) {
            log.error("Job not found for JobNo: {} during document identifier extraction. Cannot proceed.", jobNo);
            return;
        }

        try {
            // Check if documents exist in the database
            List<JobDocument> documents = jobDocumentService.getJobDocuments(jobNo);
            if (documents.isEmpty()) {
                log.warn("No documents found in database during extraction phase for Job No: {}. This may indicate a transaction isolation issue.", jobNo);

                // Try to re-fetch documents from SharePoint if needed
                log.info("Attempting to re-fetch documents from SharePoint for Job No: {}", jobNo);
                List<String> downloadedDocumentTypes = jobAttachmentService.fetchAndStoreJobAttachments(jobNo)
                        .collectList()
                        .block(java.time.Duration.ofMinutes(3)); // Explicit timeout of 3 minutes

                if (downloadedDocumentTypes == null || downloadedDocumentTypes.isEmpty()) {
                    log.error("Failed to download documents from SharePoint during extraction phase for Job No: {}", jobNo);
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                            Job.JobStatus.ERROR, List.of("Failed to download documents from SharePoint"),
                            "Document extraction failed: No documents available");
                    return;
                }

                log.info("Successfully re-fetched {} documents from SharePoint for Job No: {}: {}",
                        downloadedDocumentTypes.size(), jobNo, downloadedDocumentTypes);

                // Add a small delay to ensure the documents are fully committed
                try {
                    log.info("Waiting for re-fetched documents to be fully committed for Job No: {}", jobNo);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for documents to commit: {}", e.getMessage());
                }
            }

            // Extract document identifiers from all documents
            log.info("Extracting document identifiers for Job No: {}", jobNo);
            Map<String, String> extractedIdentifiers = jobDocumentVerificationService.extractAllDocumentIdentifiers(jobNo);
            log.info("Document identifier extraction completed for Job No: {}", jobNo);

            if (extractedIdentifiers.isEmpty()) {
                log.warn("No document identifiers were extracted for Job No: {}", jobNo);
                // Continue with the process, as the verification step will handle this case
            } else {
                log.info("Successfully extracted identifiers for Job No: {}: {}", jobNo, extractedIdentifiers);
            }
        } catch (Exception e) {
            log.error("Error during document identifier extraction for Job No: {}: {}",
                    jobNo, e.getMessage(), e);
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                    Job.JobStatus.ERROR, List.of("Error during document identifier extraction: " + e.getMessage()),
                    "Document identifier extraction failed: " + e.getMessage());
        }
    }

    /**
     * Verifies documents for a job using the extracted identifiers in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     * It uses the previously extracted document identifiers to fetch Business Central data
     * and complete the verification process.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocumentsWithExtractedIdentifiersInNewTransaction(String verificationRequestId, String jobNo) {
        log.info("Starting document verification with extracted identifiers in a new transaction for Job No: {}", jobNo);

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
            // Fetch Business Central data for verification using the extracted identifiers
            log.info("Verifying documents with extracted identifiers for Job No: {}", jobNo);
            List<String> discrepancies = jobDocumentVerificationService.verifyJobDocumentsWithExtractedIdentifiers(jobNo);

            // Update status based on verification results
            if (discrepancies.isEmpty()) {
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                        Job.JobStatus.VERIFIED, Collections.emptyList(), "Verification completed successfully");
            } else {
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                        Job.JobStatus.FLAGGED, discrepancies, "Verification completed with discrepancies");
            }
        } catch (Exception e) {
            log.error("Error during document verification with extracted identifiers for Job No: {}: {}",
                    jobNo, e.getMessage(), e);
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                    Job.JobStatus.ERROR, List.of("Error during verification: " + e.getMessage()),
                    "Verification failed: " + e.getMessage());
        }
    }

    /**
     * Verifies documents for a job using the combined classification and verification approach in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     * @deprecated Use extractDocumentIdentifiersInNewTransaction and verifyDocumentsWithExtractedIdentifiersInNewTransaction instead
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocumentsWithCombinedApproachInNewTransaction(String verificationRequestId, String jobNo) {
        log.info("Starting document verification with combined approach in a new transaction for Job No: {}", jobNo);

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
            // Fetch Business Central data for verification
            log.info("Fetching Business Central data for verification for Job No: {}", jobNo);
            var jobLedgerEntries = businessCentralService.fetchJobLedgerEntries(jobNo)
                    .collectList()
                    .block();

            if (jobLedgerEntries != null && !jobLedgerEntries.isEmpty()) {
                // Perform document verification with the combined approach
                List<String> discrepancies = jobDocumentVerificationService.verifyJobDocumentsWithCombinedApproach(
                        jobLedgerEntries.get(0), jobNo);

                // Update status based on verification results
                if (discrepancies.isEmpty()) {
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                            Job.JobStatus.VERIFIED, Collections.emptyList(), "Verification completed successfully");
                } else {
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                            Job.JobStatus.FLAGGED, discrepancies, "Verification completed with discrepancies");
                }
            } else {
                log.error("No job ledger entries found for Job No: {}", jobNo);
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                        Job.JobStatus.ERROR, List.of("No job ledger entries found"),
                        "Verification failed: No job ledger entries found");
            }
        } catch (Exception e) {
            log.error("Error during document verification with combined approach for Job No: {}: {}",
                    jobNo, e.getMessage(), e);
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                    Job.JobStatus.ERROR, List.of("Error during verification: " + e.getMessage()),
                    "Verification failed: " + e.getMessage());
        }
    }

    /**
     * Verifies documents for a job in a new transaction.
     * This method is called from orchestrateDocumentProcessing and starts a new transaction.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocumentsInNewTransaction(String verificationRequestId, String jobNo) {
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
            // First, ensure all classified documents have their document type updated
            log.info("Ensuring all classified documents have their document type updated for Job No: {}", jobNo);
            int updatedCount = documentClassificationService.updateDocumentTypesForClassifiedDocuments(jobNo);
            if (updatedCount > 0) {
                log.info("Updated {} documents with classified types for Job No: {}", updatedCount, jobNo);
            } else {
                log.info("No documents needed updating for Job No: {}", jobNo);
            }

            // Now perform the actual verification
            log.info("Starting document verification for Job No: {}", jobNo);

            // Fetch Business Central data for verification
            log.info("Fetching Business Central data for verification for Job No: {}", jobNo);
            var jobLedgerEntries = businessCentralService.fetchJobLedgerEntries(jobNo)
                    .collectList()
                    .block();

            if (jobLedgerEntries != null && !jobLedgerEntries.isEmpty()) {
                // Perform document verification with the first ledger entry
                List<String> discrepancies = jobDocumentVerificationService.verifyJobDocuments(jobLedgerEntries.get(0), jobNo);

                // Update status based on verification results
                if (discrepancies.isEmpty()) {
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                            Job.JobStatus.VERIFIED, Collections.emptyList(), "Verification completed successfully");
                } else {
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.COMPLETED,
                            Job.JobStatus.FLAGGED, discrepancies, "Verification completed with discrepancies");
                }
            } else {
                log.error("Failed to fetch Job Ledger Entries from Business Central for Job No: {}", jobNo);
                updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                        Job.JobStatus.ERROR, List.of("Failed to fetch Job Ledger Entries from Business Central"),
                        "No Job Ledger Entries found in Business Central");
            }

            log.info("Verification completed for Job No: {}", jobNo);
        } catch (Exception e) {
            // Catch any exception during verification
            log.error("Exception during verification for Job No: {}", jobNo, e);
            String errorMsg = "Error during verification: " + e.getMessage();
            updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                    Job.JobStatus.ERROR, List.of(errorMsg), e.getMessage());
        }
    }

    /**
     * Legacy method kept for backward compatibility.
     * @deprecated Use verifyDocumentsInNewTransaction instead
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocuments(String verificationRequestId, String jobNo) {
        log.warn("Using deprecated verifyDocuments method - should use verifyDocumentsInNewTransaction");
        verifyDocumentsInNewTransaction(verificationRequestId, jobNo);
    }

    /**
     * Handles errors during document processing with enhanced error classification.
     *
     * @param verificationRequestId The verification request ID
     * @param jobNo The job number
     * @param e The exception that occurred
     */
    private void handleProcessingError(String verificationRequestId, String jobNo, Exception e) {
        try {
            VerificationRequest request = verificationRequestRepository.findById(verificationRequestId).orElse(null);
            Job job = jobRepository.findByBusinessCentralJobId(jobNo).orElse(null);

            if (request != null && job != null) {
                // Check if this is a business logic error (missing identifiers) or system error
                if (e instanceof SystemErrorHandler.BusinessLogicErrorException) {
                    // Business logic error - show to user
                    String businessErrorMsg = e.getMessage();
                    log.info("Business logic error for Job No: {}: {}", jobNo, businessErrorMsg);
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                            Job.JobStatus.FLAGGED, List.of(businessErrorMsg), businessErrorMsg);
                } else if (e instanceof SystemErrorHandler.SystemErrorException) {
                    // System error - don't show details to user, retry will be handled by SystemErrorHandler
                    log.error("System error for Job No: {}: {}", jobNo, e.getMessage());
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                            Job.JobStatus.ERROR, List.of("System temporarily unavailable - processing will retry automatically"),
                            "System error: " + e.getMessage());
                } else {
                    // Unknown error type - treat as system error
                    String errorMsg = "Error during document processing: " + e.getMessage();
                    log.error("Unknown error type for Job No: {}: {}", jobNo, e.getMessage(), e);
                    updateRequestAndJobStatus(request, job, VerificationRequest.VerificationStatus.FAILED,
                            Job.JobStatus.ERROR, List.of(errorMsg), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Error handling processing error for Job No: {}: {}", jobNo, ex.getMessage(), ex);
        }
    }

    /**
     * Helper method to update statuses and log activity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRequestAndJobStatus(VerificationRequest request, Job job,
                                       VerificationRequest.VerificationStatus requestStatus, Job.JobStatus jobStatus,
                                       List<String> discrepancies, String activityLogReason) {
        try {
            log.info("Updating verification request status to {} and job status to {} for Job No: {}",
                    requestStatus, jobStatus, request.getJobNo());

            request.setStatus(requestStatus);
            request.setResultTimestamp(LocalDateTime.now());
            request.setDiscrepanciesJson(serializeDiscrepancies(discrepancies));
            verificationRequestRepository.save(request);

            String activityLogEvent = ActivityLogService.EVENT_JOB_PROCESSED; // Default
            Long jobIdForLog = null;

            // Store the verification result in the Job entity
            if (job != null && (requestStatus == VerificationRequest.VerificationStatus.COMPLETED ||
                               requestStatus == VerificationRequest.VerificationStatus.FAILED)) {
                // Store the verification result
                job.setVerificationResult(activityLogReason);

                // Set hasDiscrepancies flag
                if (discrepancies != null && !discrepancies.isEmpty()) {
                    job.setHasDiscrepancies(true);
                } else {
                    job.setHasDiscrepancies(false);
                }
            }

            if (job != null && jobStatus != null) {
                job.setStatus(jobStatus);
                job.setLastProcessedAt(LocalDateTime.now());
                jobRepository.save(job);
                jobIdForLog = job.getId(); // Use job ID if available

                log.info("Successfully updated Job No: {} to status: {} at {}",
                        request.getJobNo(), jobStatus, LocalDateTime.now());
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
                } else {
                    activityLogService.recordActivity(activityLogEvent,
                            String.format(messageFormat, request.getId(), statusText, request.getJobNo(), activityLogReason),
                            jobIdForLog, "System");
                }
            }
        } catch (Exception e) {
            log.error("Error updating request and job status: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method to serialize discrepancies to JSON.
     */
    private String serializeDiscrepancies(List<String> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return "[]";
        }

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(discrepancies);
        } catch (Exception e) {
            log.error("Error serializing discrepancies: {}", e.getMessage(), e);
            return "[]";
        }
    }
}
