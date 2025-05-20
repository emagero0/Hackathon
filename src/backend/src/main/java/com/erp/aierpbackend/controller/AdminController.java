package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.repository.JobDocumentRepository;
import com.erp.aierpbackend.service.DocumentClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for administrative operations.
 * Note: This controller should be secured or removed in production.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final JobDocumentRepository jobDocumentRepository;
    private final DocumentClassificationService documentClassificationService;

    /**
     * Deletes all job documents from the database.
     * WARNING: This operation cannot be undone.
     *
     * @return Response with the number of deleted documents
     */
    @DeleteMapping("/documents/all")
    @Transactional
    public ResponseEntity<String> deleteAllDocuments() {
        log.warn("Deleting all job documents from the database");

        // Count documents before deletion
        long documentCount = jobDocumentRepository.count();

        // Delete all documents
        jobDocumentRepository.deleteAll();

        log.info("Successfully deleted {} job documents", documentCount);

        return ResponseEntity.ok("Successfully deleted " + documentCount + " job documents");
    }

    /**
     * Updates document types for already classified documents.
     * This endpoint finds all documents with documentType = UNCLASSIFIED but with a non-null classifiedDocumentType,
     * and updates their documentType to match their classifiedDocumentType.
     *
     * @return Response with the number of updated documents
     */
    @PostMapping("/documents/update-types")
    @Transactional
    public ResponseEntity<String> updateDocumentTypes() {
        log.info("Updating document types for all classified documents");

        int updatedCount = documentClassificationService.updateDocumentTypesForClassifiedDocuments(null);

        return ResponseEntity.ok("Successfully updated " + updatedCount + " document types");
    }

    /**
     * Updates document types for already classified documents for a specific job.
     * This endpoint finds all documents for the specified job with documentType = UNCLASSIFIED but with a non-null classifiedDocumentType,
     * and updates their documentType to match their classifiedDocumentType.
     *
     * @param jobNo The job number
     * @return Response with the number of updated documents
     */
    @PostMapping("/documents/update-types/{jobNo}")
    @Transactional
    public ResponseEntity<String> updateDocumentTypesForJob(@PathVariable String jobNo) {
        log.info("Updating document types for classified documents for Job No: '{}'", jobNo);

        int updatedCount = documentClassificationService.updateDocumentTypesForClassifiedDocuments(jobNo);

        return ResponseEntity.ok("Successfully updated " + updatedCount + " document types for Job No: '" + jobNo + "'");
    }
}
