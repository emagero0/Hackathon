# Recreating the job_documents Table

This directory contains scripts to recreate the `job_documents` table if it has been accidentally deleted.

## Files Included

1. `recreate_job_documents_table.sql` - The SQL script that recreates the table with the correct structure
2. `run_recreate_table_local.bat` - Windows batch script for running on a local MySQL installation
3. `run_recreate_table_docker.sh` - Bash script for running in a Docker environment
4. `run_recreate_table_docker.ps1` - PowerShell script for running in a Docker environment on Windows

## Instructions

### Option 1: Running on Local MySQL Installation

1. Make sure MySQL is installed and running on your local machine
2. Open a command prompt in this directory
3. Run the batch script:
   ```
   run_recreate_table_local.bat
   ```
4. Enter your MySQL password when prompted

### Option 2: Running in Docker Environment (Bash)

1. Make sure your Docker containers are running
2. Open a terminal in this directory
3. Make the script executable:
   ```
   chmod +x run_recreate_table_docker.sh
   ```
4. Run the script:
   ```
   ./run_recreate_table_docker.sh
   ```
5. Enter your MySQL password when prompted

### Option 3: Running in Docker Environment (PowerShell)

1. Make sure your Docker containers are running
2. Open PowerShell in this directory
3. Run the script:
   ```
   .\run_recreate_table_docker.ps1
   ```
4. Enter your MySQL password when prompted

## Verification

After running the script, the `job_documents` table should be recreated with the following structure:

- `id` - Auto-incrementing primary key
- `job_no` - Job number (NOT NULL)
- `document_type` - Type of document (NOT NULL)
- `classified_document_type` - Type of document as classified by the AI
- `file_name` - Name of the file
- `content_type` - MIME type of the file
- `document_data` - The actual document data as LONGBLOB
- `source_url` - URL where the document was downloaded from
- `created_at` - Timestamp when the record was created (NOT NULL)

The table also has a unique constraint on `job_no` and `file_name` to prevent duplicate documents.

## Important Notes

1. The script first checks if the table already exists before attempting to create it
2. If you want to force recreation of the table (dropping it first if it exists), uncomment the DROP TABLE line in the SQL script
3. After recreation, you will need to re-download your documents from SharePoint or re-upload them manually
4. The application's automatic document download functionality should work normally after the table is recreated
