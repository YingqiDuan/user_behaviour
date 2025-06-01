@echo off
echo Starting User Behavior Query Service...
echo Port: 8082
echo Profile: query

mvn spring-boot:run -Dspring-boot.run.profiles=query 