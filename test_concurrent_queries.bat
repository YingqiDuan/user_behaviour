@echo off
echo ========================================
echo Concurrent Query Service Load Test
echo ========================================

set BASE_URL=http://localhost:8082/api/query
set CONCURRENT_REQUESTS=20
set USER_ID=user123

echo Starting %CONCURRENT_REQUESTS% concurrent requests...

for /L %%i in (1,1,%CONCURRENT_REQUESTS%) do (
    start /B curl -s "%BASE_URL%/users/%USER_ID%/events?limit=50" > nul
)

echo.
echo Waiting for requests to complete...
timeout /t 5 > nul

echo.
echo Testing different endpoints concurrently...

start /B curl -s "%BASE_URL%/stats/top-event-types" > nul
start /B curl -s "%BASE_URL%/stats/top-active-users" > nul  
start /B curl -s "%BASE_URL%/stats/today" > nul
start /B curl -s "%BASE_URL%/events?page=0&size=20" > nul
start /B curl -s "%BASE_URL%/users/%USER_ID%/summary" > nul

echo.
echo Concurrent load test completed!
echo Check server logs for performance metrics.
echo.

echo Getting final health status...
curl -s "%BASE_URL%/health"

echo.
pause 