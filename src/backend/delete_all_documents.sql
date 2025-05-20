-- Script to delete all documents from the job_documents table
-- This will remove all document data from the database

-- First, count how many documents will be deleted
SELECT COUNT(*) AS documents_to_delete FROM job_documents;

-- Delete all documents
DELETE FROM job_documents;

-- Verify that all documents have been deleted
SELECT COUNT(*) AS remaining_documents FROM job_documents;

-- Optional: Reset auto-increment counter if needed
-- ALTER TABLE job_documents AUTO_INCREMENT = 1;
