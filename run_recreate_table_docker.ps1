# PowerShell script to recreate the job_documents table in Docker

# Get the MySQL container name or ID
$MYSQL_CONTAINER = docker ps | Select-String -Pattern "mysql" | ForEach-Object { $_.ToString().Split(' ')[0] }

if (-not $MYSQL_CONTAINER) {
    Write-Host "MySQL container not found. Please make sure the container is running."
    exit 1
}

Write-Host "Found MySQL container: $MYSQL_CONTAINER"

# Copy the SQL script to the container
Write-Host "Copying SQL script to container..."
docker cp recreate_job_documents_table.sql ${MYSQL_CONTAINER}:/tmp/recreate_job_documents_table.sql

# Execute the SQL script in the container
Write-Host "Executing SQL script to recreate the job_documents table..."
$MYSQL_PASSWORD = Read-Host "Enter MySQL root password" -AsSecureString
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($MYSQL_PASSWORD)
$PlainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)

docker exec -i $MYSQL_CONTAINER mysql -u root -p"$PlainPassword" aierpdb -e "source /tmp/recreate_job_documents_table.sql"

Write-Host "Table recreation process completed."
