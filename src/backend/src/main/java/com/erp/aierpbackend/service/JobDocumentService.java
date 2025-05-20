package com.erp.aierpbackend.service;

import com.erp.aierpbackend.entity.JobDocument;
import com.erp.aierpbackend.repository.JobDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing job documents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobDocumentService {

    private final JobDocumentRepository jobDocumentRepository;

    /**
     * Save a job document.
     *
     * @param jobDocument The job document to save
     * @return The saved job document
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public JobDocument saveJobDocument(JobDocument jobDocument) {
        log.info("Saving job document for Job No: '{}', Document Type: '{}', Data Size: {} bytes",
                jobDocument.getJobNo(), jobDocument.getDocumentType(),
                jobDocument.getDocumentData() != null ? jobDocument.getDocumentData().length : 0);

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        try {
            // Check if we're creating a new document or updating an existing one
            JobDocument documentToSave = jobDocument;

            // If the document has no ID (new document), check if one already exists with the same job number and file name
            if (jobDocument.getId() == null && jobDocument.getFileName() != null) {
                Optional<JobDocument> existingDoc = jobDocumentRepository.findTopByJobNoAndFileNameOrderByIdDesc(
                        jobDocument.getJobNo(), jobDocument.getFileName());

                if (existingDoc.isPresent()) {
                    JobDocument existing = existingDoc.get();
                    log.info("Found existing document with ID: {} for Job No: '{}', File Name: '{}'. Updating instead of creating new.",
                            existing.getId(), existing.getJobNo(), existing.getFileName());

                    // Update the existing document with new data
                    existing.setDocumentType(jobDocument.getDocumentType());
                    existing.setDocumentData(jobDocument.getDocumentData());
                    existing.setContentType(jobDocument.getContentType());
                    existing.setSourceUrl(jobDocument.getSourceUrl());

                    // If the new document has a classified type, use it
                    if (jobDocument.getClassifiedDocumentType() != null && !jobDocument.getClassifiedDocumentType().isEmpty()) {
                        existing.setClassifiedDocumentType(jobDocument.getClassifiedDocumentType());
                    }

                    documentToSave = existing;
                }
            }

            // Save the document
            JobDocument saved = jobDocumentRepository.save(documentToSave);

            // Force immediate flush to database
            jobDocumentRepository.flush();

            // Log success
            log.info("Successfully saved job document with ID: {} for Job No: '{}', Document Type: '{}', Classified Type: '{}'",
                    saved.getId(), saved.getJobNo(), saved.getDocumentType(),
                    saved.getClassifiedDocumentType() != null ? saved.getClassifiedDocumentType() : "null");

            // Add a small delay to ensure the save is fully committed
            try {
                log.debug("Adding a small delay to ensure document save is fully committed");
                Thread.sleep(200); // 200ms delay
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrupted while waiting for document save to commit", ie);
            }

            return saved;
        } catch (Exception e) {
            log.error("Error saving job document for Job No: '{}', Document Type: '{}': {}",
                    jobDocument.getJobNo(), jobDocument.getDocumentType(), e.getMessage(), e);

            // If the error is related to data size, log a specific message
            if (e.getMessage() != null && e.getMessage().contains("Data too long for column 'document_data'")) {
                log.error("Document data is too large for the database column. Please run the SQL script to alter the table: ALTER TABLE job_documents MODIFY COLUMN document_data LONGBLOB;");
            }

            throw e;
        }
    }

    /**
     * Get all documents for a specific job.
     *
     * @param jobNo The job number
     * @return List of job documents
     */
    public List<JobDocument> getJobDocuments(String jobNo) {
        log.info("Getting all documents for Job No: '{}'", jobNo);
        return jobDocumentRepository.findByJobNo(jobNo);
    }

    /**
     * Get all documents in the database.
     * Note: Use with caution on large databases.
     *
     * @return List of all job documents
     */
    public List<JobDocument> getAllJobDocuments() {
        log.info("Getting all documents from the database");
        return jobDocumentRepository.findAll();
    }

    /**
     * Get all documents for a specific job and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return List of job documents
     */
    public List<JobDocument> getJobDocumentsByType(String jobNo, String documentType) {
        log.info("Getting documents for Job No: '{}', Document Type: '{}'", jobNo, documentType);
        return jobDocumentRepository.findAllByJobNoAndDocumentType(jobNo, documentType);
    }

    /**
     * Get all documents for a specific job and classified document type.
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return List of job documents
     */
    public List<JobDocument> getJobDocumentsByClassifiedType(String jobNo, String classifiedDocumentType) {
        log.info("Getting documents for Job No: '{}', Classified Document Type: '{}'", jobNo, classifiedDocumentType);
        return jobDocumentRepository.findAllByJobNoAndClassifiedDocumentType(jobNo, classifiedDocumentType);
    }

    /**
     * Get a document by job number and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return Optional containing the document if found
     */
    public Optional<JobDocument> getJobDocument(String jobNo, String documentType) {
        log.info("Getting document for Job No: '{}', Document Type: '{}'", jobNo, documentType);

        // Normalize parameters if needed
        String normalizedJobNo = jobNo.trim();
        String normalizedDocumentType = documentType.trim();

        if (!normalizedJobNo.equals(jobNo) || !normalizedDocumentType.equals(documentType)) {
            log.debug("Normalized parameters - Original Job No: '{}', Normalized: '{}', Original Document Type: '{}', Normalized: '{}'",
                    jobNo, normalizedJobNo, documentType, normalizedDocumentType);
        }

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Always get the most recent document by using the OrderByIdDesc method
        Optional<JobDocument> result = jobDocumentRepository.findTopByJobNoAndDocumentTypeOrderByIdDesc(normalizedJobNo, normalizedDocumentType);
        if (result.isPresent()) {
            JobDocument document = result.get();
            log.info("Found document with ID: {} for Job No: '{}', Document Type: '{}'",
                    document.getId(), document.getJobNo(), document.getDocumentType());
            log.debug("Document details - File Name: '{}', Content Type: '{}', Data Size: {} bytes",
                    document.getFileName(), document.getContentType(),
                    document.getDocumentData() != null ? document.getDocumentData().length : 0);
        } else {
            log.warn("Document not found for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);
        }

        return result;
    }

    /**
     * Get a document by job number and classified document type.
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return Optional containing the document if found
     */
    public Optional<JobDocument> getJobDocumentByClassifiedType(String jobNo, String classifiedDocumentType) {
        log.info("Getting document for Job No: '{}', Classified Document Type: '{}'", jobNo, classifiedDocumentType);

        // Normalize parameters if needed
        String normalizedJobNo = jobNo.trim();
        String normalizedClassifiedDocumentType = classifiedDocumentType.trim();

        if (!normalizedJobNo.equals(jobNo) || !normalizedClassifiedDocumentType.equals(classifiedDocumentType)) {
            log.debug("Normalized parameters - Original Job No: '{}', Normalized: '{}', Original Classified Document Type: '{}', Normalized: '{}'",
                    jobNo, normalizedJobNo, classifiedDocumentType, normalizedClassifiedDocumentType);
        }

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Always get the most recent document by using the OrderByIdDesc method
        Optional<JobDocument> result = jobDocumentRepository.findTopByJobNoAndClassifiedDocumentTypeOrderByIdDesc(normalizedJobNo, normalizedClassifiedDocumentType);
        if (result.isPresent()) {
            JobDocument document = result.get();
            log.info("Found document with ID: {} for Job No: '{}', Classified Document Type: '{}'",
                    document.getId(), document.getJobNo(), document.getClassifiedDocumentType());
            log.debug("Document details - File Name: '{}', Content Type: '{}', Data Size: {} bytes",
                    document.getFileName(), document.getContentType(),
                    document.getDocumentData() != null ? document.getDocumentData().length : 0);
        } else {
            log.warn("Document not found for Job No: '{}', Classified Document Type: '{}'", normalizedJobNo, normalizedClassifiedDocumentType);
        }

        return result;
    }

    /**
     * Delete all documents for a specific job.
     *
     * @param jobNo The job number
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteJobDocuments(String jobNo) {
        log.info("Deleting all documents for Job No: '{}'", jobNo);

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // Get count before deletion for logging
        List<JobDocument> documents = jobDocumentRepository.findByJobNo(jobNo);
        int count = documents.size();

        jobDocumentRepository.deleteByJobNo(jobNo);
        jobDocumentRepository.flush(); // Force immediate flush

        log.info("Successfully deleted {} documents for Job No: '{}'", count, jobNo);
    }

    /**
     * Check if a document exists for a specific job and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return True if the document exists, false otherwise
     */
    public boolean documentExists(String jobNo, String documentType) {
        log.debug("Checking if document exists for Job No: '{}', Document Type: '{}'", jobNo, documentType);

        // Normalize parameters if needed
        String normalizedJobNo = jobNo.trim();
        String normalizedDocumentType = documentType.trim();

        if (!normalizedJobNo.equals(jobNo) || !normalizedDocumentType.equals(documentType)) {
            log.debug("Normalized parameters - Original Job No: '{}', Normalized: '{}', Original Document Type: '{}', Normalized: '{}'",
                    jobNo, normalizedJobNo, documentType, normalizedDocumentType);
        }

        // Log transaction information if possible
        try {
            log.debug("Transaction active: {}, Transaction name: {}",
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName());
        } catch (Exception e) {
            log.debug("Could not log transaction details: {}", e.getMessage());
        }

        // First check by document type
        boolean existsByType = jobDocumentRepository.findByJobNoAndDocumentType(normalizedJobNo, normalizedDocumentType).isPresent();

        // If not found by document type, check by classified document type
        boolean existsByClassifiedType = false;
        if (!existsByType) {
            existsByClassifiedType = jobDocumentRepository.findByJobNoAndClassifiedDocumentType(normalizedJobNo, normalizedDocumentType).isPresent();
            if (existsByClassifiedType) {
                log.debug("Document found by classified document type for Job No: '{}', Document Type: '{}'", normalizedJobNo, normalizedDocumentType);
            }
        }

        boolean exists = existsByType || existsByClassifiedType;
        log.debug("Document exists for Job No: '{}', Document Type: '{}': {}", normalizedJobNo, normalizedDocumentType, exists);
        return exists;
    }
}
