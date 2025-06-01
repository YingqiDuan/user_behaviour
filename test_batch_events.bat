@echo off
echo ====================================
echo Batch Event Testing for Data Storage
echo ====================================

echo.
echo Step 1: Send 5 test events to trigger batch processing
FOR /L %%i IN (1,1,5) DO (
    echo Sending event %%i...
    if %%i LSS 10 (
        curl -X POST http://localhost:8080/api/events -H "Content-Type: application/json" -d "{\"userId\":\"batch-test-0%%i\",\"eventType\":\"PAGE_VIEW\",\"source\":\"batch-test\",\"eventTime\":\"2025-05-31T23:59:0%%i\",\"eventData\":{\"page\":\"/test-0%%i\",\"batch\":true},\"sessionId\":\"batch-session-0%%i\",\"deviceInfo\":\"Test Browser 0%%i\",\"ipAddress\":\"127.0.0.%%i\"}"
    ) else (
        curl -X POST http://localhost:8080/api/events -H "Content-Type: application/json" -d "{\"userId\":\"batch-test-%%i\",\"eventType\":\"PAGE_VIEW\",\"source\":\"batch-test\",\"eventTime\":\"2025-05-31T23:59:%%i\",\"eventData\":{\"page\":\"/test-%%i\",\"batch\":true},\"sessionId\":\"batch-session-%%i\",\"deviceInfo\":\"Test Browser %%i\",\"ipAddress\":\"127.0.0.%%i\"}"
    )
    timeout /t 1 /nobreak > nul
)

echo.
echo Step 2: Wait 15 seconds for processing
timeout /t 15 /nobreak > nul

echo.
echo Step 3: Check processing service statistics
curl -s http://localhost:8081/api/stats

echo.
echo Step 4: Trigger manual flush to ensure all events are processed
curl -s http://localhost:8081/api/stats/flush

echo.
echo Step 5: Final database verification
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb -e "SELECT COUNT(*) as total_events FROM user_behavior_events; SELECT user_id, event_type, source FROM user_behavior_events WHERE user_id LIKE 'batch-test%' ORDER BY processed_time DESC;" 2>nul

echo.
echo Batch testing completed! 