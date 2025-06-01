@echo off
echo ========================================
echo Redis Cache Performance Test
echo ========================================

echo.
echo Starting cache performance test...
echo This will send multiple requests to test cache hit rates

set USER_ID=testuser123
set ITERATIONS=10

echo.
echo Testing user recent events cache (User: %USER_ID%)
echo Sending %ITERATIONS% requests...

for /L %%i in (1,1,%ITERATIONS%) do (
    echo Request %%i...
    curl -s "http://localhost:8081/api/cache/users/%USER_ID%/recent-events?limit=10" > nul
    timeout /t 1 > nul
)

echo.
echo Testing hot data report cache...
echo Sending %ITERATIONS% requests...

for /L %%i in (1,1,%ITERATIONS%) do (
    echo Request %%i...
    curl -s "http://localhost:8081/api/cache/hot-data/report" > nul
    timeout /t 1 > nul
)

echo.
echo Testing top event types cache...
echo Sending %ITERATIONS% requests...

for /L %%i in (1,1,%ITERATIONS%) do (
    echo Request %%i...
    curl -s "http://localhost:8081/api/cache/stats/top-event-types?topN=5" > nul
    timeout /t 1 > nul
)

echo.
echo Performance test completed!
echo.
echo Getting final cache statistics...
curl -s "http://localhost:8081/api/cache/stats/cache"

echo.
echo.
echo Getting processing statistics...
curl -s "http://localhost:8081/api/cache/stats/processing"

echo.
echo ========================================
echo Cache Performance Test Completed!
echo ========================================
pause 