#!/bin/bash
# Script to recreate the job_documents table in Docker

# Get the MySQL container name or ID
MYSQL_CONTAINER=$(docker ps | grep mysql | awk '{print $1}')

if [ -z "$MYSQL_CONTAINER" ]; then
    echo "MySQL container not found. Please make sure the container is running."
    exit 1
fi

echo "Found MySQL container: $MYSQL_CONTAINER"

# Copy the SQL script to the container
echo "Copying SQL script to container..."
docker cp recreate_job_documents_table.sql $MYSQL_CONTAINER:/tmp/recreate_job_documents_table.sql

# Execute the SQL script in the container
echo "Executing SQL script to recreate the job_documents table..."
read -sp "Enter MySQL root password: " MYSQL_PASSWORD
echo ""

docker exec -i $MYSQL_CONTAINER mysql -u root -p$MYSQL_PASSWORD aierpdb < /tmp/recreate_job_documents_table.sql

echo "Table recreation process completed."
