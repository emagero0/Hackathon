-- Script to recreate the job_documents table
-- Run this script to recreate the table after it has been accidentally deleted

-- First, check if the table exists
SELECT COUNT(*) AS table_exists 
FROM information_schema.tables 
WHERE table_schema = DATABASE() 
AND table_name = 'job_documents';

-- Drop the table if it exists (comment this out if you want to be extra safe)
-- DROP TABLE IF EXISTS job_documents;

-- Create the job_documents table
CREATE TABLE IF NOT EXISTS job_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_no VARCHAR(255) NOT NULL,
    document_type VARCHAR(255) NOT NULL,
    classified_document_type VARCHAR(255),
    file_name VARCHAR(255),
    content_type VARCHAR(255),
    document_data LONGBLOB NOT NULL,
    source_url VARCHAR(1024),
    created_at DATETIME NOT NULL
);

-- Add unique constraint on job_no and file_name to prevent duplicate documents
ALTER TABLE job_documents 
ADD CONSTRAINT uk_job_documents_job_no_file_name UNIQUE (job_no, file_name);

-- Verify the table was created correctly
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    CHARACTER_MAXIMUM_LENGTH, 
    IS_NULLABLE
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'job_documents'
ORDER BY 
    ORDINAL_POSITION;

-- Verify the unique constraint was added
SELECT 
    CONSTRAINT_NAME,
    COLUMN_NAME
FROM 
    INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'job_documents'
    AND CONSTRAINT_NAME = 'uk_job_documents_job_no_file_name';
