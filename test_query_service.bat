@echo off
echo ========================================
echo Testing Query Service RESTful APIs
echo ========================================

set BASE_URL=http://localhost:8082/api/query

echo.
echo 1. Health Check...
curl -s "%BASE_URL%/health" | echo.

echo.
echo 2. Query user events for user123...
curl -s "%BASE_URL%/users/user123/events?limit=10" | echo.

echo.
echo 3. Get top event types...
curl -s "%BASE_URL%/stats/top-event-types?topN=5" | echo.

echo.
echo 4. Get user summary for user123...
curl -s "%BASE_URL%/stats/user/user123/summary" | echo.

echo.
echo 5. Get top active users...
curl -s "%BASE_URL%/stats/top-active-users?topN=5" | echo.

echo.
echo 6. Get all events (paginated)...
curl -s "%BASE_URL%/events?page=0&size=5" | echo.

echo.
echo 7. Get today's statistics...
curl -s "%BASE_URL%/stats/today" | echo.

echo.
echo 8. Get current hour statistics...
curl -s "%BASE_URL%/stats/current-hour" | echo.

echo.
echo 9. Get events by type (PAGE_VIEW)...
curl -s "%BASE_URL%/events/type/PAGE_VIEW?page=0&size=5" | echo.

echo.
echo ========================================
echo Query Service API test completed!
echo ========================================
pause 