package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.JobDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for JobDocument entity.
 */
@Repository
public interface JobDocumentRepository extends JpaRepository<JobDocument, Long> {

    /**
     * Find all documents for a specific job.
     *
     * @param jobNo The job number
     * @return List of job documents
     */
    List<JobDocument> findByJobNo(String jobNo);

    /**
     * Find all documents for a specific job, ordered by ID descending (most recent first).
     *
     * @param jobNo The job number
     * @return List of job documents
     */
    List<JobDocument> findByJobNoOrderByIdDesc(String jobNo);

    /**
     * Find a document by job number and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return Optional containing the document if found
     */
    Optional<JobDocument> findByJobNoAndDocumentType(String jobNo, String documentType);

    /**
     * Find the most recent document by job number and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return Optional containing the most recent document if found
     */
    Optional<JobDocument> findTopByJobNoAndDocumentTypeOrderByIdDesc(String jobNo, String documentType);

    /**
     * Find a document by job number and classified document type.
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return Optional containing the document if found
     */
    Optional<JobDocument> findByJobNoAndClassifiedDocumentType(String jobNo, String classifiedDocumentType);

    /**
     * Find the most recent document by job number and classified document type.
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return Optional containing the most recent document if found
     */
    Optional<JobDocument> findTopByJobNoAndClassifiedDocumentTypeOrderByIdDesc(String jobNo, String classifiedDocumentType);

    /**
     * Find all documents by job number and document type.
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return List of job documents
     */
    List<JobDocument> findAllByJobNoAndDocumentType(String jobNo, String documentType);

    /**
     * Find all documents by job number and document type, ordered by ID descending (most recent first).
     *
     * @param jobNo The job number
     * @param documentType The document type
     * @return List of job documents
     */
    List<JobDocument> findAllByJobNoAndDocumentTypeOrderByIdDesc(String jobNo, String documentType);

    /**
     * Find all documents by job number and classified document type.
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return List of job documents
     */
    List<JobDocument> findAllByJobNoAndClassifiedDocumentType(String jobNo, String classifiedDocumentType);

    /**
     * Find all documents by job number and classified document type, ordered by ID descending (most recent first).
     *
     * @param jobNo The job number
     * @param classifiedDocumentType The classified document type
     * @return List of job documents
     */
    List<JobDocument> findAllByJobNoAndClassifiedDocumentTypeOrderByIdDesc(String jobNo, String classifiedDocumentType);

    /**
     * Find a document by job number and file name.
     *
     * @param jobNo The job number
     * @param fileName The file name
     * @return Optional containing the document if found
     */
    Optional<JobDocument> findByJobNoAndFileName(String jobNo, String fileName);

    /**
     * Find the most recent document by job number and file name.
     *
     * @param jobNo The job number
     * @param fileName The file name
     * @return Optional containing the most recent document if found
     */
    Optional<JobDocument> findTopByJobNoAndFileNameOrderByIdDesc(String jobNo, String fileName);

    /**
     * Check if a document exists with the given job number and file name.
     *
     * @param jobNo The job number
     * @param fileName The file name
     * @return True if a document exists, false otherwise
     */
    boolean existsByJobNoAndFileName(String jobNo, String fileName);

    /**
     * Find documents that have been classified but still have UNCLASSIFIED as document type.
     *
     * @param jobNo The job number
     * @param documentType The document type (typically "UNCLASSIFIED")
     * @return List of documents that need updating
     */
    @Query("SELECT d FROM JobDocument d WHERE d.jobNo = :jobNo AND d.documentType = :documentType AND d.classifiedDocumentType IS NOT NULL AND d.classifiedDocumentType <> ''")
    List<JobDocument> findClassifiedButNotUpdatedDocuments(@Param("jobNo") String jobNo, @Param("documentType") String documentType);

    /**
     * Delete all documents for a specific job.
     *
     * @param jobNo The job number
     */
    void deleteByJobNo(String jobNo);
}
