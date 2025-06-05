@echo off
echo Starting Eureka Server (Service Registry)...
echo Port: 8761
echo Dashboard: http://localhost:8761
.\mvnw spring-boot:run -Peureka 