-- Add unique constraint on job_no and file_name to prevent duplicate documents
ALTER TABLE job_documents ADD CONSTRAINT uk_job_documents_job_no_file_name UNIQUE (job_no, file_name);
