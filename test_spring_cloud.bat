@echo off
echo ========================================
echo Testing Spring Cloud Features
echo ========================================

echo.
echo 1. Checking Eureka Server Dashboard...
curl -s http://localhost:8761/eureka/apps | findstr "application"

echo.
echo 2. Checking service registrations...
echo Producer Service Health:
curl -s http://localhost:8080/actuator/health

echo.
echo Consumer Service Health:
curl -s http://localhost:8081/actuator/health

echo.
echo Query Service Health:
curl -s http://localhost:8082/actuator/health

echo.
echo 3. Testing Feign inter-service communication...
echo Calling Query Service -> Processing Service via Feign:
curl -s http://localhost:8082/api/query/processing/stats

echo.
echo 4. Testing system status (multiple services):
curl -s http://localhost:8082/api/query/system/status

echo.
echo 5. Checking service discovery...
echo Registered services in Eureka:
curl -s http://localhost:8761/eureka/apps | findstr -i "user-behavior"

echo.
echo ========================================
echo Spring Cloud test completed!
echo ========================================
pause 