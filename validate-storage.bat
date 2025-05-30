@echo off
echo ===== User Behavior Event Data Storage Validation =====
echo.

REM Check if MySQL service is running
echo Checking MySQL service...
docker ps | findstr mysql > nul
if %ERRORLEVEL% neq 0 (
    echo Error: MySQL container is not running, please ensure Docker containers are started
    echo Please run: docker-compose up -d mysql
    exit /b 1
)
echo MySQL service is running
echo.

REM Validate table structure
echo Validating database structure...
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb -e "SHOW TABLES LIKE 'user_behavior_events'" > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Table doesn't exist. Creating user_behavior_events table...
    mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb < src/main/resources/scripts/create_table.sql
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to create table
        exit /b 1
    )
    echo Table created successfully
) else (
    echo Table exists
)
echo.

REM Execute validation script
echo Executing data validation script...
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb < src/main/resources/scripts/validate_data_storage.sql

REM Optionally run validation test
set /p run_test=Run end-to-end validation test? (y/n): 
if "%run_test%"=="y" (
    echo Running end-to-end validation test...
    mvnw test -Dtest=DataStorageValidationTest
)

echo.
echo Validation complete 