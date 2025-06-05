#!/bin/bash

# 用户行为微服务 Kubernetes 部署脚本
set -e

echo "🚀 开始部署用户行为微服务到 Kubernetes..."

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

# 检查 kubectl 是否可用
if ! command -v kubectl &> /dev/null; then
    print_error "kubectl 未安装或不在PATH中"
    exit 1
fi

# 检查集群连接
if ! kubectl cluster-info &> /dev/null; then
    print_error "无法连接到 Kubernetes 集群，请检查 kubeconfig"
    exit 1
fi

print_success "成功连接到 Kubernetes 集群"

# 获取脚本目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

# 1. 创建命名空间
print_message "创建命名空间..."
kubectl apply -f namespace.yaml

# 2. 部署配置映射和密钥
print_message "部署配置映射和密钥..."
kubectl apply -f base/configmap.yaml
kubectl apply -f base/secret.yaml

# 3. 部署基础设施服务 (如果需要)
print_message "检查是否需要部署基础设施服务..."
if [ -f "infrastructure/kafka.yaml" ]; then
    print_message "部署 Kafka..."
    kubectl apply -f infrastructure/kafka.yaml
fi

if [ -f "infrastructure/mysql.yaml" ]; then
    print_message "部署 MySQL..."
    kubectl apply -f infrastructure/mysql.yaml
fi

if [ -f "infrastructure/redis.yaml" ]; then
    print_message "部署 Redis..."
    kubectl apply -f infrastructure/redis.yaml
fi

# 4. 部署 Eureka Server (服务发现)
print_message "部署 Eureka Server..."
kubectl apply -f base/eureka-server.yaml

# 等待 Eureka Server 就绪
print_message "等待 Eureka Server 就绪..."
kubectl wait --for=condition=ready pod -l app=eureka-server -n user-behavior-platform --timeout=300s

# 5. 部署数据收集服务
print_message "部署数据收集服务..."
kubectl apply -f base/data-collector.yaml

# 6. 部署事件处理服务
if [ -f "base/event-processor.yaml" ]; then
    print_message "部署事件处理服务..."
    kubectl apply -f base/event-processor.yaml
fi

# 7. 部署查询服务
if [ -f "base/query-service.yaml" ]; then
    print_message "部署查询服务..."
    kubectl apply -f base/query-service.yaml
fi

# 8. 部署 Ingress
print_message "部署 Ingress..."
kubectl apply -f base/ingress.yaml

# 9. 等待所有服务就绪
print_message "等待所有服务就绪..."
kubectl wait --for=condition=ready pod -l app=data-collector -n user-behavior-platform --timeout=300s

# 10. 显示部署状态
print_message "显示部署状态..."
echo ""
print_message "命名空间状态:"
kubectl get ns user-behavior-platform

echo ""
print_message "Pod 状态:"
kubectl get pods -n user-behavior-platform

echo ""
print_message "Service 状态:"
kubectl get svc -n user-behavior-platform

echo ""
print_message "Ingress 状态:"
kubectl get ingress -n user-behavior-platform

echo ""
print_message "HPA 状态:"
kubectl get hpa -n user-behavior-platform

# 11. 显示访问信息
echo ""
print_success "🎉 部署完成!"
echo ""
print_message "访问信息:"
echo "  📊 Eureka Dashboard: http://dashboard.user-behavior.example.com/eureka"
echo "  🌐 API 端点: https://api.user-behavior.example.com"
echo "  📝 数据收集: https://api.user-behavior.example.com/collect"
echo "  🔍 查询服务: https://api.user-behavior.example.com/api/query"
echo ""
print_warning "注意: 请确保DNS指向正确的Ingress IP地址"

# 12. 获取Ingress IP
INGRESS_IP=$(kubectl get ingress user-behavior-ingress -n user-behavior-platform -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "Pending")
if [ "$INGRESS_IP" != "Pending" ] && [ -n "$INGRESS_IP" ]; then
    echo ""
    print_success "Ingress IP: $INGRESS_IP"
    echo "请将以下DNS记录添加到您的DNS提供商:"
    echo "  api.user-behavior.example.com      A    $INGRESS_IP"
    echo "  dashboard.user-behavior.example.com A    $INGRESS_IP"
else
    print_warning "Ingress IP 尚未分配，请稍后检查: kubectl get ingress -n user-behavior-platform"
fi

print_success "部署脚本执行完成!" 