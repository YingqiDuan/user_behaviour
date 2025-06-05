@echo off
echo ========================================
echo Starting User Behavior Microservices
echo ========================================

echo.
echo Step 1: Starting Eureka Server (Service Registry)...
start "Eureka Server" cmd /c ".\mvnw spring-boot:run -Peureka"

echo Waiting 30 seconds for Eureka Server to start...
timeout /t 30 /nobreak > nul

echo.
echo Step 2: Starting Producer Service...
start "Producer Service" cmd /c ".\mvnw spring-boot:run -Pproducer"

echo.
echo Step 3: Starting Consumer Service...
start "Consumer Service" cmd /c ".\mvnw spring-boot:run -Pconsumer"

echo.
echo Step 4: Starting Query Service...
start "Query Service" cmd /c ".\mvnw spring-boot:run -Pquery"

echo.
echo ========================================
echo All services are starting up!
echo ========================================
echo.
echo Service URLs:
echo - Eureka Dashboard: http://localhost:8761
echo - Producer Service: http://localhost:8080
echo - Consumer Service: http://localhost:8081
echo - Query Service:    http://localhost:8082
echo.
echo Wait 1-2 minutes for all services to register with Eureka
echo ======================================== 