@echo off
echo ========================================
echo Testing Redis Cache Functionality
echo ========================================

echo.
echo 1. Checking Redis connection health...
curl -s "http://localhost:8081/api/cache/health/redis" | echo.

echo.
echo 2. Getting cache statistics...
curl -s "http://localhost:8081/api/cache/stats/cache" | echo.

echo.
echo 3. Getting processing statistics...
curl -s "http://localhost:8081/api/cache/stats/processing" | echo.

echo.
echo 4. Getting top event types...
curl -s "http://localhost:8081/api/cache/stats/top-event-types?topN=5" | echo.

echo.
echo 5. Getting top active users...
curl -s "http://localhost:8081/api/cache/stats/top-active-users?topN=5" | echo.

echo.
echo 6. Getting hot data report...
curl -s "http://localhost:8081/api/cache/hot-data/report" | echo.

echo.
echo 7. Testing user recent events (user123)...
curl -s "http://localhost:8081/api/cache/users/user123/recent-events?limit=10" | echo.

echo.
echo 8. Getting user behavior heat map (user123)...
curl -s "http://localhost:8081/api/cache/hot-data/users/user123/heatmap" | echo.

echo.
echo ========================================
echo Cache functionality test completed!
echo ========================================
pause 