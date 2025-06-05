# 用户行为微服务 - Docker 容器化与 Kubernetes 部署指南

本文档详细介绍了用户行为微服务系统的完整容器化和 Kubernetes 部署流程。

## 📋 目录

- [前提条件](#前提条件)
- [项目架构](#项目架构)
- [Docker 容器化](#docker-容器化)
- [本地开发环境](#本地开发环境)
- [Kubernetes 部署](#kubernetes-部署)
- [监控和运维](#监控和运维)
- [故障排查](#故障排查)

## 🛠️ 前提条件

### 本地开发环境
- Docker Desktop 4.0+
- Docker Compose 2.0+
- Maven 3.8+
- JDK 17+

### Kubernetes 部署环境
- Kubernetes 1.23+
- kubectl 已配置并连接到集群
- NGINX Ingress Controller
- 存储类 (StorageClass) 用于持久化存储

## 🏗️ 项目架构

### 微服务组件
- **Eureka Server**: 服务注册与发现
- **Data Collector**: 数据收集服务 (Producer)
- **Event Processor**: 事件处理服务 (Consumer)
- **Query Service**: 查询服务

### 基础设施组件
- **Kafka**: 消息队列
- **MySQL**: 数据存储
- **Redis**: 缓存
- **Zookeeper**: Kafka 协调服务

## 🐳 Docker 容器化

### 1. 构建 Docker 镜像

#### 使用 Maven Jib 插件 (推荐)
```bash
# 构建所有微服务镜像
./docker/scripts/build-images.sh   # Linux/macOS
./docker/scripts/build-images.bat  # Windows

# 或手动构建单个服务
mvn compile jib:dockerBuild -Peureka -Djib.to.image=user-behavior/eureka-server:1.0.0
mvn compile jib:dockerBuild -Pproducer -Djib.to.image=user-behavior/data-collector:1.0.0
mvn compile jib:dockerBuild -Pconsumer -Djib.to.image=user-behavior/event-processor:1.0.0
mvn compile jib:dockerBuild -Pquery -Djib.to.image=user-behavior/query-service:1.0.0
```

#### 使用传统 Dockerfile
```bash
# 为不同的微服务构建镜像
docker build -t user-behavior/eureka-server:1.0.0 --build-arg PROFILE=eureka .
docker build -t user-behavior/data-collector:1.0.0 --build-arg PROFILE=producer .
docker build -t user-behavior/event-processor:1.0.0 --build-arg PROFILE=consumer .
docker build -t user-behavior/query-service:1.0.0 --build-arg PROFILE=query .
```

### 2. 镜像优化特性

#### 多阶段构建
- **构建阶段**: 使用 `openjdk:17-jdk-alpine` 编译应用
- **运行阶段**: 使用 `openjdk:17-jre-alpine` 减少镜像大小

#### 安全优化
- 使用非 root 用户 (uid:gid = 1001:1001)
- 最小化基础镜像攻击面
- 环境变量注入敏感配置

#### 性能优化
- JVM 容器感知参数
- G1 垃圾收集器
- 内存使用限制 (MaxRAMPercentage=75%)

### 3. 镜像推送到仓库

```bash
# 登录 Docker Hub
docker login

# 标记镜像
docker tag user-behavior/eureka-server:1.0.0 your-username/eureka-server:1.0.0
docker tag user-behavior/data-collector:1.0.0 your-username/data-collector:1.0.0
docker tag user-behavior/event-processor:1.0.0 your-username/event-processor:1.0.0
docker tag user-behavior/query-service:1.0.0 your-username/query-service:1.0.0

# 推送镜像
docker push your-username/eureka-server:1.0.0
docker push your-username/data-collector:1.0.0
docker push your-username/event-processor:1.0.0
docker push your-username/query-service:1.0.0
```

## 🏠 本地开发环境

### 1. 启动基础设施服务

```bash
# 启动所有服务 (基础设施 + 微服务)
docker-compose up -d

# 仅启动基础设施服务
docker-compose up -d zookeeper kafka mysql redis

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 2. 服务访问信息

| 服务 | 内部地址 | 外部地址 | 用途 |
|------|----------|----------|------|
| Kafka | kafka:9092 | localhost:29092 | 消息队列 |
| MySQL | mysql:3306 | localhost:3306 | 数据存储 |
| Redis | redis:6379 | localhost:6379 | 缓存 |
| Eureka | eureka-server:8761 | localhost:8761 | 服务发现 |
| Data Collector | data-collector:8080 | localhost:8080 | 数据收集 |
| Event Processor | event-processor:8081 | localhost:8081 | 事件处理 |
| Query Service | query-service:8082 | localhost:8082 | 查询服务 |

### 3. 本地测试

```bash
# 测试数据收集接口
curl -X POST http://localhost:8080/collect \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "eventType": "PAGE_VIEW",
    "source": "web",
    "eventTime": "2024-01-01T10:00:00"
  }'

# 测试查询接口
curl http://localhost:8082/api/users/test-user/events

# 查看 Eureka 注册服务
curl http://localhost:8761/eureka/apps
```

## ☸️ Kubernetes 部署

### 1. 准备 Kubernetes 集群

#### 云平台推荐
- **Google GKE**: 易于使用，与 Google Cloud 服务集成
- **Amazon EKS**: AWS 生态系统
- **Azure AKS**: Microsoft Azure 生态系统
- **阿里云 ACK**: 国内用户推荐

#### 本地测试
```bash
# 使用 Minikube
minikube start --memory=8192 --cpus=4

# 使用 Kind
kind create cluster --config=k8s/kind-config.yaml

# 启用 NGINX Ingress (Minikube)
minikube addons enable ingress
```

### 2. 部署到 Kubernetes

#### 自动化部署 (推荐)
```bash
# 使用部署脚本
chmod +x k8s/deploy.sh
./k8s/deploy.sh
```

#### 手动部署
```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 部署配置和密钥
kubectl apply -f k8s/base/configmap.yaml
kubectl apply -f k8s/base/secret.yaml

# 3. 部署服务 (按依赖顺序)
kubectl apply -f k8s/base/eureka-server.yaml
kubectl apply -f k8s/base/data-collector.yaml
kubectl apply -f k8s/base/event-processor.yaml
kubectl apply -f k8s/base/query-service.yaml

# 4. 部署 Ingress
kubectl apply -f k8s/base/ingress.yaml

# 5. 检查部署状态
kubectl get pods -n user-behavior-platform
kubectl get svc -n user-behavior-platform
kubectl get ingress -n user-behavior-platform
```

### 3. 访问应用

#### 通过 Ingress 访问
- **API 端点**: `https://api.user-behavior.example.com`
- **Eureka Dashboard**: `http://dashboard.user-behavior.example.com/eureka`

#### 通过 Port Forward 访问 (测试)
```bash
# 数据收集服务
kubectl port-forward svc/data-collector 8080:8080 -n user-behavior-platform

# 查询服务
kubectl port-forward svc/query-service 8082:8082 -n user-behavior-platform

# Eureka 服务
kubectl port-forward svc/eureka-server 8761:8761 -n user-behavior-platform
```

### 4. 弹性伸缩配置

#### 水平 Pod 自动伸缩 (HPA)
```bash
# 查看 HPA 状态
kubectl get hpa -n user-behavior-platform

# 手动伸缩
kubectl scale deployment data-collector --replicas=5 -n user-behavior-platform

# 查看伸缩历史
kubectl describe hpa data-collector-hpa -n user-behavior-platform
```

#### 垂直 Pod 自动伸缩 (VPA)
```yaml
# 如果集群支持 VPA
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: data-collector-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: data-collector
  updatePolicy:
    updateMode: "Auto"
```

## 📊 监控和运维

### 1. 健康检查

```bash
# 检查 Pod 健康状态
kubectl get pods -n user-behavior-platform

# 查看 Pod 详细信息
kubectl describe pod <pod-name> -n user-behavior-platform

# 查看应用日志
kubectl logs -f <pod-name> -n user-behavior-platform

# 查看所有服务的日志
kubectl logs -f -l app.kubernetes.io/name=user-behavior-platform -n user-behavior-platform
```

### 2. 性能监控

#### 使用 Prometheus + Grafana
```bash
# 安装 Prometheus Operator
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack

# 访问 Grafana
kubectl port-forward svc/prometheus-grafana 3000:80
```

#### 使用云平台监控
- **GKE**: Google Cloud Monitoring
- **EKS**: Amazon CloudWatch
- **AKS**: Azure Monitor

### 3. 日志收集

#### 使用 ELK Stack
```bash
# 安装 Elastic Stack
helm repo add elastic https://helm.elastic.co
helm install elasticsearch elastic/elasticsearch
helm install kibana elastic/kibana
helm install filebeat elastic/filebeat
```

## 🔧 故障排查

### 1. 常见问题

#### Pod 启动失败
```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n user-behavior-platform

# 查看容器日志
kubectl logs <pod-name> -c <container-name> -n user-behavior-platform

# 进入容器调试
kubectl exec -it <pod-name> -n user-behavior-platform -- /bin/sh
```

#### 服务发现问题
```bash
# 检查 Eureka 注册状态
kubectl port-forward svc/eureka-server 8761:8761 -n user-behavior-platform
# 访问 http://localhost:8761

# 检查 DNS 解析
kubectl exec -it <pod-name> -n user-behavior-platform -- nslookup eureka-server
```

#### 网络连接问题
```bash
# 测试服务间连接
kubectl exec -it <pod-name> -n user-behavior-platform -- wget -qO- http://eureka-server:8761/actuator/health

# 检查 Ingress 配置
kubectl describe ingress user-behavior-ingress -n user-behavior-platform
```

### 2. 性能调优

#### JVM 参数调优
```yaml
env:
- name: JAVA_OPTS
  value: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### 资源限制调优
```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

### 3. 数据备份与恢复

#### MySQL 数据备份
```bash
# 创建备份任务
kubectl create job mysql-backup --from=cronjob/mysql-backup -n user-behavior-platform

# 手动备份
kubectl exec -it mysql-0 -n user-behavior-platform -- mysqldump -u user -ppassword userdb > backup.sql
```

#### Redis 数据备份
```bash
# Redis 持久化配置已启用 AOF
kubectl exec -it redis-0 -n user-behavior-platform -- redis-cli BGREWRITEAOF
```

## 🔒 安全最佳实践

### 1. 镜像安全
- 使用官方基础镜像
- 定期更新依赖版本
- 扫描镜像漏洞
- 使用非 root 用户

### 2. 集群安全
- 启用 RBAC
- 使用 Network Policies
- 配置 Pod Security Standards
- 定期更新 Kubernetes 版本

### 3. 配置安全
- 敏感信息使用 Secret
- 配置环境变量注入
- 启用静态加密
- 定期轮换密钥

## 📚 参考资源

- [Docker 官方文档](https://docs.docker.com/)
- [Kubernetes 官方文档](https://kubernetes.io/docs/)
- [Spring Boot Docker 指南](https://spring.io/guides/gs/spring-boot-docker/)
- [Google Jib 文档](https://github.com/GoogleContainerTools/jib)

---

**注意**: 本文档中的示例配置适用于开发和测试环境。生产环境部署请根据实际需求调整资源配置、安全设置和监控方案。 