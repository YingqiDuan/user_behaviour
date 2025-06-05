@echo off
REM 用户行为微服务Docker镜像构建脚本 (Windows版本)
REM 此脚本用于构建所有微服务的Docker镜像

setlocal EnableDelayedExpansion

echo 🚀 开始构建用户行为微服务Docker镜像...

REM 检查Docker是否安装
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker 未安装或不在PATH中
    exit /b 1
)

REM 检查Maven是否安装
mvn --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Maven 未安装或不在PATH中
    exit /b 1
)

REM 项目根目录
cd /d "%~dp0..\.."
echo 📁 项目根目录: %CD%

REM 清理之前的构建
echo 🧹 清理之前的构建...
call mvn clean

REM 构建微服务镜像函数
:build_service_image
set profile=%~1
set service_name=%~2
set port=%~3

echo 🔨 构建 %service_name% 镜像 (Profile: %profile%)...

REM 使用 Jib 构建镜像
call mvn compile jib:dockerBuild -P%profile% -Djib.to.image=user-behavior/%service_name%:1.0.0
if errorlevel 1 (
    echo ❌ %service_name% 镜像构建失败
    exit /b 1
) else (
    echo ✅ %service_name% 镜像构建成功
)
goto :eof

echo 🏗️ 开始构建所有微服务镜像...

REM 1. 构建 Eureka Server
call :build_service_image "eureka" "eureka-server" "8761"

REM 2. 构建 Data Collector (Producer)
call :build_service_image "producer" "data-collector" "8080"

REM 3. 构建 Event Processor (Consumer)
call :build_service_image "consumer" "event-processor" "8081"

REM 4. 构建 Query Service
call :build_service_image "query" "query-service" "8082"

REM 显示构建的镜像
echo 📋 显示构建的镜像:
docker images | findstr "user-behavior"

echo 🎉 所有微服务镜像构建完成!

REM 可选：推送到仓库
set /p push_choice="是否要推送镜像到Docker Hub? (y/N): "
if /i "%push_choice%"=="y" (
    echo 📤 推送镜像到Docker Hub...
    
    REM 检查是否已登录Docker Hub
    docker info | findstr "Username" >nul 2>&1
    if errorlevel 1 (
        echo ⚠️ 请先登录Docker Hub: docker login
        exit /b 1
    )
    
    REM 推送镜像
    for %%s in (eureka-server data-collector event-processor query-service) do (
        echo 📤 推送 %%s 镜像...
        docker tag user-behavior/%%s:1.0.0 your-dockerhub-username/%%s:1.0.0
        docker push your-dockerhub-username/%%s:1.0.0
    )
    
    echo ✅ 镜像推送完成!
)

echo ✅ 脚本执行完成!
pause 