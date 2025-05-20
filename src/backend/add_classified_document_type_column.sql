-- Script to add the classified_document_type column to the job_documents table
-- Run this script to add the column if it doesn't exist

-- Check if the column already exists
SELECT 
    COLUMN_NAME
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'job_documents' 
    AND COLUMN_NAME = 'classified_document_type';

-- If the column doesn't exist, add it
ALTER TABLE job_documents 
ADD COLUMN classified_document_type VARCHAR(255) NULL AFTER document_type;

-- Verify the column was added
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
    AND COLUMN_NAME = 'classified_document_type';
