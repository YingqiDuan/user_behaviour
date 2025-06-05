#!/bin/bash

# 用户行为微服务Docker镜像构建脚本
# 此脚本用于构建所有微服务的Docker镜像

set -e

echo "🚀 开始构建用户行为微服务Docker镜像..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数：打印彩色消息
print_message() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✅${NC} $1"
}

print_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ❌${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️${NC} $1"
}

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    print_error "Docker 未安装或不在PATH中"
    exit 1
fi

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    print_error "Maven 未安装或不在PATH中"
    exit 1
fi

# 项目根目录
PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$PROJECT_ROOT"

print_message "项目根目录: $PROJECT_ROOT"

# 清理之前的构建
print_message "清理之前的构建..."
mvn clean

# 构建微服务镜像
build_service_image() {
    local profile=$1
    local service_name=$2
    local port=$3
    
    print_message "构建 $service_name 镜像 (Profile: $profile)..."
    
    # 使用 Jib 构建镜像
    if mvn compile jib:dockerBuild -P$profile -Djib.to.image=user-behavior/$service_name:1.0.0; then
        print_success "$service_name 镜像构建成功"
    else
        print_error "$service_name 镜像构建失败"
        return 1
    fi
}

# 构建所有微服务镜像
print_message "开始构建所有微服务镜像..."

# 1. 构建 Eureka Server
build_service_image "eureka" "eureka-server" "8761"

# 2. 构建 Data Collector (Producer)
build_service_image "producer" "data-collector" "8080"

# 3. 构建 Event Processor (Consumer)
build_service_image "consumer" "event-processor" "8081"

# 4. 构建 Query Service
build_service_image "query" "query-service" "8082"

# 显示构建的镜像
print_message "显示构建的镜像:"
docker images | grep "user-behavior"

print_success "🎉 所有微服务镜像构建完成!"

# 可选：推送到仓库
read -p "是否要推送镜像到Docker Hub? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_message "推送镜像到Docker Hub..."
    
    # 检查是否已登录Docker Hub
    if ! docker info | grep -q "Username"; then
        print_warning "请先登录Docker Hub: docker login"
        exit 1
    fi
    
    # 推送镜像
    for service in eureka-server data-collector event-processor query-service; do
        print_message "推送 $service 镜像..."
        docker tag user-behavior/$service:1.0.0 your-dockerhub-username/$service:1.0.0
        docker push your-dockerhub-username/$service:1.0.0
    done
    
    print_success "镜像推送完成!"
fi

print_success "脚本执行完成!" 