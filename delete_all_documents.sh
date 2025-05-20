#!/bin/bash
# Script to delete all documents from the database in Docker

# Get the MySQL container name or ID
MYSQL_CONTAINER=$(docker ps | grep mysql | awk '{print $1}')

if [ -z "$MYSQL_CONTAINER" ]; then
    echo "MySQL container not found. Please make sure the container is running."
    exit 1
fi

echo "Found MySQL container: $MYSQL_CONTAINER"

# Copy the SQL script to the container
echo "Copying SQL script to container..."
docker cp src/backend/delete_all_documents.sql $MYSQL_CONTAINER:/tmp/delete_all_documents.sql

# Execute the SQL script in the container
echo "Executing SQL script to delete all documents..."
docker exec -i $MYSQL_CONTAINER mysql -u root -p aierpdb < /tmp/delete_all_documents.sql

echo "Document deletion complete."
