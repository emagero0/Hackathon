package com.erp.aierpbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling temporary document storage.
 * This service stores documents in a temporary directory and provides methods to access them.
 * Documents are automatically cleaned up after a configurable time period.
 */
@Service
@Slf4j
public class TemporaryDocumentStorageService {

    // In-memory storage for document metadata
    private final ConcurrentHashMap<String, Map<String, TempDocumentInfo>> jobDocumentsMap = new ConcurrentHashMap<>();
    
    // Base directory for temporary files
    private final Path tempDir;
    
    // Document type constants (copied from JobAttachmentService)
    public static final String SALES_QUOTE_TYPE = "SalesQuote";
    public static final String PROFORMA_INVOICE_TYPE = "ProformaInvoice";
    public static final String JOB_CONSUMPTION_TYPE = "JobConsumption";
    public static final String UNCLASSIFIED_TYPE = "UNCLASSIFIED";

    /**
     * Constructor that creates the temporary directory if it doesn't exist.
     */
    public TemporaryDocumentStorageService() throws IOException {
        // Create a subdirectory in the system temp directory
        this.tempDir = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "erp-temp-docs"));
        log.info("Temporary document storage initialized at: {}", tempDir);
        
        // Add shutdown hook to clean up temp files when application exits
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupAllTempFiles));
    }

    /**
     * Stores a document in temporary storage.
     *
     * @param jobNo The job number
     * @param fileName The file name
     * @param contentType The content type
     * @param documentData The document data
     * @param sourceUrl The source URL
     * @return A unique ID for the stored document
     */
    public String storeDocument(String jobNo, String fileName, String contentType, byte[] documentData, String sourceUrl) {
        try {
            // Generate a unique ID for this document
            String documentId = UUID.randomUUID().toString();
            
            // Create a file path for the document
            Path filePath = tempDir.resolve(documentId);
            
            // Write the document data to the file
            Files.write(filePath, documentData);
            
            // Store metadata in memory
            TempDocumentInfo docInfo = new TempDocumentInfo(
                    documentId,
                    jobNo,
                    fileName,
                    contentType,
                    filePath.toString(),
                    sourceUrl,
                    UNCLASSIFIED_TYPE,
                    null
            );
            
            // Add to the job documents map
            jobDocumentsMap.computeIfAbsent(jobNo, k -> new ConcurrentHashMap<>())
                    .put(documentId, docInfo);
            
            log.info("Stored temporary document for Job No: '{}', File Name: '{}', ID: '{}'", 
                    jobNo, fileName, documentId);
            
            return documentId;
        } catch (IOException e) {
            log.error("Error storing temporary document for Job No: '{}', File Name: '{}'", 
                    jobNo, fileName, e);
            throw new RuntimeException("Failed to store temporary document", e);
        }
    }

    /**
     * Retrieves a document from temporary storage.
     *
     * @param jobNo The job number
     * @param documentId The document ID
     * @return The document data
     */
    public byte[] getDocumentData(String jobNo, String documentId) {
        try {
            Map<String, TempDocumentInfo> jobDocs = jobDocumentsMap.get(jobNo);
            if (jobDocs == null) {
                log.warn("No documents found for Job No: '{}'", jobNo);
                return null;
            }
            
            TempDocumentInfo docInfo = jobDocs.get(documentId);
            if (docInfo == null) {
                log.warn("Document not found for Job No: '{}', Document ID: '{}'", jobNo, documentId);
                return null;
            }
            
            // Read the document data from the file
            return Files.readAllBytes(Paths.get(docInfo.getFilePath()));
        } catch (IOException e) {
            log.error("Error retrieving temporary document for Job No: '{}', Document ID: '{}'", 
                    jobNo, documentId, e);
            throw new RuntimeException("Failed to retrieve temporary document", e);
        }
    }

    /**
     * Updates the document type for a temporary document.
     *
     * @param jobNo The job number
     * @param documentId The document ID
     * @param documentType The new document type
     */
    public void updateDocumentType(String jobNo, String documentId, String documentType) {
        Map<String, TempDocumentInfo> jobDocs = jobDocumentsMap.get(jobNo);
        if (jobDocs == null) {
            log.warn("No documents found for Job No: '{}' when updating document type", jobNo);
            return;
        }
        
        TempDocumentInfo docInfo = jobDocs.get(documentId);
        if (docInfo == null) {
            log.warn("Document not found for Job No: '{}', Document ID: '{}' when updating document type", 
                    jobNo, documentId);
            return;
        }
        
        // Update the document type
        docInfo.setClassifiedDocumentType(documentType);
        log.info("Updated document type for Job No: '{}', Document ID: '{}' to '{}'", 
                jobNo, documentId, documentType);
    }

    /**
     * Gets all documents for a job.
     *
     * @param jobNo The job number
     * @return A map of document IDs to document info
     */
    public Map<String, TempDocumentInfo> getJobDocuments(String jobNo) {
        return jobDocumentsMap.getOrDefault(jobNo, new HashMap<>());
    }

    /**
     * Gets documents of a specific type for a job.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return A map of document IDs to document info
     */
    public Map<String, TempDocumentInfo> getJobDocumentsByType(String jobNo, String documentType) {
        Map<String, TempDocumentInfo> result = new HashMap<>();
        Map<String, TempDocumentInfo> jobDocs = jobDocumentsMap.get(jobNo);
        
        if (jobDocs != null) {
            jobDocs.forEach((id, info) -> {
                if (documentType.equals(info.getClassifiedDocumentType())) {
                    result.put(id, info);
                }
            });
        }
        
        return result;
    }

    /**
     * Cleans up temporary files for a job.
     *
     * @param jobNo The job number
     */
    public void cleanupJobTempFiles(String jobNo) {
        Map<String, TempDocumentInfo> jobDocs = jobDocumentsMap.remove(jobNo);
        if (jobDocs != null) {
            jobDocs.forEach((id, info) -> {
                try {
                    Files.deleteIfExists(Paths.get(info.getFilePath()));
                    log.debug("Deleted temporary file for Job No: '{}', Document ID: '{}'", jobNo, id);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file for Job No: '{}', Document ID: '{}'", 
                            jobNo, id, e);
                }
            });
            log.info("Cleaned up temporary files for Job No: '{}'", jobNo);
        }
    }

    /**
     * Cleans up all temporary files.
     */
    public void cleanupAllTempFiles() {
        log.info("Cleaning up all temporary files");
        jobDocumentsMap.forEach((jobNo, jobDocs) -> {
            jobDocs.forEach((id, info) -> {
                try {
                    Files.deleteIfExists(Paths.get(info.getFilePath()));
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file for Job No: '{}', Document ID: '{}'", 
                            jobNo, id, e);
                }
            });
        });
        jobDocumentsMap.clear();
        log.info("All temporary files cleaned up");
    }

    /**
     * Inner class to store document metadata.
     */
    public static class TempDocumentInfo {
        private final String id;
        private final String jobNo;
        private final String fileName;
        private final String contentType;
        private final String filePath;
        private final String sourceUrl;
        private String documentType;
        private String classifiedDocumentType;

        public TempDocumentInfo(String id, String jobNo, String fileName, String contentType, 
                               String filePath, String sourceUrl, String documentType, 
                               String classifiedDocumentType) {
            this.id = id;
            this.jobNo = jobNo;
            this.fileName = fileName;
            this.contentType = contentType;
            this.filePath = filePath;
            this.sourceUrl = sourceUrl;
            this.documentType = documentType;
            this.classifiedDocumentType = classifiedDocumentType;
        }

        // Getters and setters
        public String getId() { return id; }
        public String getJobNo() { return jobNo; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public String getFilePath() { return filePath; }
        public String getSourceUrl() { return sourceUrl; }
        public String getDocumentType() { return documentType; }
        public String getClassifiedDocumentType() { return classifiedDocumentType; }
        
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public void setClassifiedDocumentType(String classifiedDocumentType) { 
            this.classifiedDocumentType = classifiedDocumentType; 
        }
    }
}
