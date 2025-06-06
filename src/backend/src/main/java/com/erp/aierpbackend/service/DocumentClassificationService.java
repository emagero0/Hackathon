package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.gemini.DocumentClassificationResult;
import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.repository.JobDocumentRepository;
import com.erp.aierpbackend.util.PdfImageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Service for classifying job documents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentClassificationService {

    private final LLMProxyService llmProxyService;
    private final JobDocumentService jobDocumentService;
    private final JobDocumentRepository jobDocumentRepository;
    private final PdfImageConverter pdfImageConverter;

    // Document type constants
    public static final String UNCLASSIFIED_TYPE = "UNCLASSIFIED";
    public static final String SALES_QUOTE_TYPE = "SalesQuote";
    public static final String PROFORMA_INVOICE_TYPE = "ProformaInvoice";
    public static final String JOB_CONSUMPTION_TYPE = "JobConsumption";

    /**
     * Classifies all unclassified documents for a job.
     *
     * @param jobNo The job number
     * @return Mono containing a map of document IDs to their classified types
     */
    /**
     * Classifies all unclassified documents for a job.
     * This method blocks until all documents are classified or timeout occurs.
     *
     * @param jobNo The job number
     * @param timeoutSeconds Maximum time to wait for classification in seconds
     * @return Map of document IDs to their classified types
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Map<Long, String> classifyJobDocumentsBlocking(String jobNo, int timeoutSeconds) {
        log.info("Classifying documents for Job No: '{}' with timeout of {} seconds", jobNo, timeoutSeconds);

        try {
            // First, update any documents that might have been classified but not updated
            int updatedCount = updateDocumentTypesForClassifiedDocuments(jobNo);
            if (updatedCount > 0) {
                log.info("Updated {} already classified documents for Job No: '{}'", updatedCount, jobNo);

                // No need for delay with proper transaction management
                log.debug("Document type updates committed with proper transaction management");
            }

            // Call the reactive method and block with timeout
            Map<Long, String> result = classifyJobDocuments(jobNo)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnError(e -> log.error("Timeout or error occurred while classifying documents for Job No: '{}': {}",
                        jobNo, e.getMessage(), e))
                .onErrorReturn(Collections.emptyMap())
                .block(Duration.ofSeconds(timeoutSeconds + 5)); // Add 5 seconds buffer to the timeout

            // No need for delay with proper transaction management
            log.debug("Classification changes committed with proper transaction management");

            return result;
        } catch (Exception e) {
            log.error("Exception while waiting for document classification for Job No: '{}': {}",
                    jobNo, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<Map<Long, String>> classifyJobDocuments(String jobNo) {
        log.info("Classifying documents for Job No: '{}'", jobNo);

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Get all unclassified documents for this job
        return Mono.fromCallable(() -> jobDocumentService.getJobDocumentsByType(jobNo, UNCLASSIFIED_TYPE))
            .flatMap(documents -> {
                if (documents.isEmpty()) {
                    log.warn("No unclassified documents found for Job No: '{}'", jobNo);
                    return Mono.just(Collections.emptyMap());
                }

                log.info("Found {} unclassified documents for Job No: '{}'", documents.size(), jobNo);

                // Process each document for classification
                List<Mono<Map.Entry<Long, String>>> classificationTasks = new ArrayList<>();

                for (JobDocument document : documents) {
                    // Convert document to images
                    Mono<List<byte[]>> imagesMono = Mono.fromCallable(() -> convertDocumentToImages(document))
                        .onErrorResume(e -> {
                            log.error("Error converting document to images for Job No: '{}', Document ID: {}: {}",
                                    jobNo, document.getId(), e.getMessage(), e);
                            return Mono.just(Collections.emptyList());
                        });

                    // Send to LLM for classification with timeout
                    Mono<Map.Entry<Long, String>> classificationMono = imagesMono
                        .flatMap(images -> {
                            if (images.isEmpty()) {
                                log.warn("Could not convert document to images for Job No: '{}', Document ID: {}",
                                        jobNo, document.getId());
                                return Mono.just(Map.entry(document.getId(), UNCLASSIFIED_TYPE));
                            }

                            return llmProxyService.classifyDocument(jobNo, images)
                                .timeout(Duration.ofSeconds(30)) // Add 30-second timeout for LLM call
                                .flatMap(classification -> {
                                    String classifiedType = classification.getDocumentType();
                                    log.info("Document classified as '{}' for Job No: '{}', Document ID: {}",
                                            classifiedType, jobNo, document.getId());

                                    // Update document with classified type
                                    document.setClassifiedDocumentType(classifiedType);

                                    // Also update the document type if it's a known type (not UNKNOWN)
                                    if (classifiedType != null && !classifiedType.equals("UNKNOWN")) {
                                        // Keep track of the original document type for logging
                                        String originalType = document.getDocumentType();

                                        // Update the document type to match the classified type
                                        document.setDocumentType(classifiedType);

                                        log.info("Updated document type from '{}' to '{}' for Job No: '{}', Document ID: {}",
                                                originalType, classifiedType, jobNo, document.getId());
                                    } else {
                                        log.warn("Document classified as UNKNOWN or null for Job No: '{}', Document ID: {}. Keeping original document type: '{}'",
                                                jobNo, document.getId(), document.getDocumentType());
                                    }

                                    return Mono.fromCallable(() -> jobDocumentService.saveJobDocument(document))
                                        .thenReturn(Map.entry(document.getId(), classification.getDocumentType()));
                                })
                                .onErrorResume(e -> {
                                    log.error("Error classifying document for Job No: '{}', Document ID: {}: {}",
                                            jobNo, document.getId(), e.getMessage(), e);
                                    return Mono.just(Map.entry(document.getId(), UNCLASSIFIED_TYPE));
                                });
                        });

                    classificationTasks.add(classificationMono);
                }

                // Wait for all classifications to complete with concurrent processing
                return Flux.mergeSequential(classificationTasks)
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
            });
    }

    /**
     * Converts a document to images.
     *
     * @param document The document to convert
     * @return List of document images as byte arrays
     * @throws IOException If there's an error converting the document
     */
    private List<byte[]> convertDocumentToImages(JobDocument document) throws IOException {
        log.debug("Converting document to images for Job No: '{}', Document ID: {}",
                document.getJobNo(), document.getId());

        byte[] documentData = document.getDocumentData();
        if (documentData == null || documentData.length == 0) {
            log.warn("Document data is empty for Job No: '{}', Document ID: {}",
                    document.getJobNo(), document.getId());
            return Collections.emptyList();
        }

        String contentType = document.getContentType();
        if (contentType != null && contentType.equals("application/pdf")) {
            // For PDF files, convert to images
            // Create a temporary file
            Path tempFile = Files.createTempFile("temp_pdf_", ".pdf");
            Files.write(tempFile, documentData);

            // Convert PDF to images
            List<byte[]> images = pdfImageConverter.convertPdfToImages(tempFile.toString(), 300f);

            // Delete the temporary file
            Files.delete(tempFile);

            return images;
        } else {
            // For image files, return as is
            return List.of(documentData);
        }
    }

    /**
     * Updates document types for already classified documents.
     * This method finds all documents with documentType = UNCLASSIFIED but with a non-null classifiedDocumentType,
     * and updates their documentType to match their classifiedDocumentType.
     *
     * @param jobNo The job number (optional, if null will process all jobs)
     * @return Number of documents updated
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public int updateDocumentTypesForClassifiedDocuments(String jobNo) {
        log.info("Updating document types for already classified documents" + (jobNo != null ? " for Job No: '" + jobNo + "'" : ""));

        List<JobDocument> documentsToUpdate;

        if (jobNo != null) {
            // Use the new repository method to find documents that need updating
            documentsToUpdate = jobDocumentRepository.findClassifiedButNotUpdatedDocuments(jobNo, UNCLASSIFIED_TYPE);
            log.info("Found {} documents that need updating for Job No: '{}'", documentsToUpdate.size(), jobNo);
        } else {
            // Get all documents (use with caution on large databases)
            documentsToUpdate = jobDocumentService.getAllJobDocuments();
            log.info("Found {} documents that might need updating across all jobs", documentsToUpdate.size());
        }

        int updatedCount = 0;

        for (JobDocument document : documentsToUpdate) {
            // Double-check if document is classified but still has UNCLASSIFIED as document type
            if (UNCLASSIFIED_TYPE.equals(document.getDocumentType()) &&
                document.getClassifiedDocumentType() != null &&
                !document.getClassifiedDocumentType().isEmpty() &&
                !document.getClassifiedDocumentType().equals("UNKNOWN")) {

                String classifiedType = document.getClassifiedDocumentType();

                log.info("Updating document type from '{}' to '{}' for Job No: '{}', Document ID: {}",
                        document.getDocumentType(), classifiedType, document.getJobNo(), document.getId());

                // Update document type to match classified type
                document.setDocumentType(classifiedType);
                JobDocument savedDocument = jobDocumentService.saveJobDocument(document);

                // Verify the update was successful
                if (classifiedType.equals(savedDocument.getDocumentType())) {
                    log.info("Successfully updated document type to '{}' for Job No: '{}', Document ID: {}",
                            classifiedType, document.getJobNo(), document.getId());
                    updatedCount++;
                } else {
                    log.warn("Failed to update document type for Job No: '{}', Document ID: {}. Expected: '{}', Actual: '{}'",
                            document.getJobNo(), document.getId(), classifiedType, savedDocument.getDocumentType());
                }
            }
        }

        // Force a flush to ensure all updates are committed
        jobDocumentRepository.flush();

        log.info("Updated {} documents with classified types", updatedCount);
        return updatedCount;
    }
}
