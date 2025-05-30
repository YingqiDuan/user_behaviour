#!/bin/bash

echo "===== User Behavior Event Data Storage Validation ====="
echo ""

# Check if MySQL service is running
echo "Checking MySQL service..."
docker ps | grep mysql > /dev/null
if [ $? -ne 0 ]; then
    echo "Error: MySQL container is not running, please ensure Docker containers are started"
    echo "Please run: docker-compose up -d mysql"
    exit 1
fi
echo "MySQL service is running"
echo ""

# Validate table structure
echo "Validating database structure..."
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb -e "SHOW TABLES LIKE 'user_behavior_events'" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Table doesn't exist. Creating user_behavior_events table..."
    mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb < src/main/resources/scripts/create_table.sql
    if [ $? -ne 0 ]; then
        echo "Error: Failed to create table"
        exit 1
    fi
    echo "Table created successfully"
else
    echo "Table exists"
fi
echo ""

# Execute validation script
echo "Executing data validation script..."
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb < src/main/resources/scripts/validate_data_storage.sql

# Optionally run validation test
read -p "Run end-to-end validation test? (y/n): " run_test
if [ "$run_test" = "y" ]; then
    echo "Running end-to-end validation test..."
    ./mvnw test -Dtest=DataStorageValidationTest
fi

echo ""
echo "Validation complete" 