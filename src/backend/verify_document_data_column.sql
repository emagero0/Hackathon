-- Script to verify the document_data column in job_documents table
-- Run this script to check if the column is already LONGBLOB
-- If not, it will alter the table to use LONGBLOB

-- Check the current column definition
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    CHARACTER_OCTET_LENGTH
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'job_documents' 
    AND COLUMN_NAME = 'document_data';

-- If the column is not LONGBLOB, run the following command to alter it
-- ALTER TABLE job_documents MODIFY COLUMN document_data LONGBLOB;

-- After altering the table, verify the change
-- SELECT 
--     TABLE_SCHEMA,
--     TABLE_NAME,
--     COLUMN_NAME,
--     DATA_TYPE,
--     CHARACTER_MAXIMUM_LENGTH,
--     CHARACTER_OCTET_LENGTH
-- FROM 
--     INFORMATION_SCHEMA.COLUMNS 
-- WHERE 
--     TABLE_SCHEMA = DATABASE() 
--     AND TABLE_NAME = 'job_documents' 
--     AND COLUMN_NAME = 'document_data';
