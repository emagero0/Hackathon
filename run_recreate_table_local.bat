@echo off
REM Script to recreate the job_documents table on a local MySQL installation

echo Running SQL script to recreate job_documents table...

REM Replace these with your actual MySQL credentials if different
set MYSQL_USER=root
set MYSQL_DB=aierpdb

REM Prompt for password
set /p MYSQL_PASSWORD="Enter MySQL password: "

REM Run the SQL script
mysql -u %MYSQL_USER% -p%MYSQL_PASSWORD% %MYSQL_DB% < recreate_job_documents_table.sql

echo Table recreation process completed.
pause
