@echo off
echo ====================================
echo End-to-End Data Storage Validation Test
echo ====================================

echo.
echo Step 1: Send test event
curl -X POST http://localhost:8080/api/events -H "Content-Type: application/json" -d "{\"userId\":\"validation-test-final\",\"eventType\":\"PAGE_VIEW\",\"source\":\"end-to-end-test\",\"eventTime\":\"2025-05-31T23:59:00\",\"eventData\":{\"page\":\"/validation\",\"test\":true},\"sessionId\":\"validation-session\",\"deviceInfo\":\"Test Browser\",\"ipAddress\":\"192.168.1.1\"}"

echo.
echo Step 2: Wait 20 seconds for event processing
timeout /t 20 /nobreak > nul

echo.
echo Step 3: Check processing service statistics
curl -s http://localhost:8081/api/stats

echo.
echo Step 4: Check Kafka consumer group status
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group userBehaviorListener --describe

echo.
echo Step 5: Check database data
mysql -h127.0.0.1 -P3306 -uuser -ppassword userdb -e "SELECT COUNT(*) as total_events FROM user_behavior_events; SELECT user_id, event_type, source, event_time FROM user_behavior_events WHERE user_id LIKE 'validation-test%' ORDER BY processed_time DESC LIMIT 3;" 2>nul

echo.
echo Validation completed! 