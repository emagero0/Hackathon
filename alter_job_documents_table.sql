-- Alter the job_documents table to use LONGBLOB for document_data column
ALTER TABLE job_documents MODIFY COLUMN document_data LONGBLOB;
