# User Behavior Event Data Storage Validation Guide

This document provides detailed steps and tools for validating the storage of user behavior event data.

## Database Structure

The system uses MySQL database to store user behavior event data, with the main table structure as follows:

- Table name: `user_behavior_events`
- Main fields:
  - `id`: Auto-increment primary key
  - `user_id`: User ID
  - `event_type`: Event type
  - `event_time`: Event time
  - `event_data`: Event details (JSON format stored as text)
  - Other fields: `source`, `session_id`, `device_info`, `ip_address`, `processed_time`, `topic`, `partition`, `offset`

- Indexes:
  - `idx_user_id`: User ID index
  - `idx_event_type`: Event type index
  - `idx_event_time`: Event time index

## Validation Tools

The project provides the following validation tools:

### 1. Database Validation Script

SQL script location: `src/main/resources/scripts/validate_data_storage.sql`

This script provides the following validation functions:
- Check if the table exists
- Check the table structure
- View total data volume
- View data distribution by event type
- Check the latest 10 event records
- Check if indexes are properly created

### 2. Table Creation Script

SQL script location: `src/main/resources/scripts/create_table.sql`

This script creates the `user_behavior_events` table with all necessary fields and indexes if it doesn't already exist.

### 3. Automated Validation Scripts

Choose one of the following scripts based on your operating system:
- Linux/Mac: `./validate-storage.sh`
- Windows: `validate-storage.bat`

These scripts will:
1. Check if the MySQL container is running
2. Verify that the data table exists
3. Create the table if it doesn't exist using the creation script
4. Execute the SQL validation script
5. Optionally run an end-to-end validation test

### 4. End-to-End Validation Test

Test class: `src/test/java/com/microservice/user_behaviour/DataStorageValidationTest.java`

This test performs a complete end-to-end test:
1. Create a test event
2. Send it to Kafka
3. Wait for processing
4. Verify the data is correctly stored in MySQL

Run command:
```
./mvnw test -Dtest=DataStorageValidationTest
```

### 5. Test Event Generator

Class: `src/main/java/com/microservice/user_behaviour/util/TestEventGenerator.java`

This tool can generate random test events and send them to the system for bulk testing of data storage.

Run command:
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=generator [event_count] [delay_ms]
```

For example, to generate 100 events with a 200ms delay between each:
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=generator 100 200
```

## Validation Steps

The complete data storage validation steps are as follows:

1. Ensure all services are running:
   ```
   docker-compose up -d
   ```

2. Run the automated validation script:
   ```
   ./validate-storage.sh  # Linux/Mac
   validate-storage.bat   # Windows
   ```
   The script will automatically create the database table if it doesn't exist.

3. Generate test events:
   ```
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=generator 20
   ```

4. Check the consumer logs:
   ```
   docker logs -f user-behavior-consumer
   ```

5. Run the validation script again to confirm that new events have been properly stored:
   ```
   ./validate-storage.sh  # Linux/Mac
   validate-storage.bat   # Windows
   ```

6. Connect directly to MySQL for queries (optional):
   ```
   mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb
   ```

## Troubleshooting

If validation fails, check:

1. Whether all services are started:
   ```
   docker ps
   ```

2. Whether Kafka topics are created:
   ```
   docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```

3. View consumer error logs:
   ```
   docker logs user-behavior-consumer
   ```

4. View MySQL error logs:
   ```
   docker logs mysql
   ```

5. Check if the table is correctly created:
   ```
   mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb -e "SHOW CREATE TABLE user_behavior_events"
   ```

6. Manually create the table if needed:
   ```
   mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb < src/main/resources/scripts/create_table.sql
   ``` 