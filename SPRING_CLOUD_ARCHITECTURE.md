# Spring Cloud 微服务架构文档

## 🏗️ 架构概览

本项目已完整实现Spring Cloud微服务架构，包含服务注册发现、负载均衡、服务间通信和监控功能。

```
                    ┌─────────────────┐
                    │ Eureka Server   │
                    │    :8761        │
                    │ 服务注册中心      │
                    └─────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │  Producer   │ │  Consumer   │ │   Query     │
    │   :8080     │ │   :8081     │ │   :8082     │
    │ +Eureka     │ │ +Eureka     │ │ +Eureka     │
    │ +Actuator   │ │ +Actuator   │ │ +Actuator   │
    └─────────────┘ └─────────────┘ └─────────────┘
            │               │               │
            │               │       Feign调用
            │               │               │
            └──────Kafka────┘               │
                            └───────────────┘
```

## 🎯 核心功能实现

### 1. **服务注册与发现 (Eureka)**
- ✅ **Eureka Server**: 独立的服务注册中心 (8761端口)
- ✅ **服务注册**: 所有微服务自动注册到Eureka
- ✅ **健康检查**: 自动检测服务健康状态
- ✅ **服务发现**: 客户端自动发现可用服务实例

### 2. **微服务间通信 (OpenFeign)**
- ✅ **声明式客户端**: 使用@FeignClient注解
- ✅ **负载均衡**: 自动轮询调用多个实例
- ✅ **熔断降级**: Fallback机制处理服务异常
- ✅ **超时控制**: 配置调用超时时间

### 3. **监控与健康检查 (Actuator)**
- ✅ **健康端点**: /actuator/health
- ✅ **指标监控**: /actuator/metrics
- ✅ **环境信息**: /actuator/env
- ✅ **应用信息**: /actuator/info

## 🚀 服务详情

### **Eureka Server** (端口: 8761)
```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication
```

**访问地址**: http://localhost:8761

### **Producer Service** (端口: 8080)
```properties
spring.application.name=user-behavior-producer
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

**功能**: 接收用户行为数据，发送到Kafka

### **Consumer Service** (端口: 8081)
```properties
spring.application.name=user-behavior-consumer
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

**功能**: 消费Kafka消息，存储到数据库和缓存

### **Query Service** (端口: 8082)
```properties
spring.application.name=user-behavior-query
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

**功能**: 提供查询API，通过Feign调用其他服务

## 🔗 服务间通信示例

### Feign客户端定义
```java
@FeignClient(name = "user-behavior-consumer", fallback = ProcessingServiceClientFallback.class)
public interface ProcessingServiceClient {
    
    @GetMapping("/api/stats")
    Map<String, Object> getProcessingStats();
    
    @GetMapping("/api/cache/health/redis")
    Map<String, Object> getCacheHealth();
}
```

### 新增API端点
```bash
# 通过Feign调用处理服务
GET /api/query/processing/stats

# 获取完整系统状态
GET /api/query/system/status
```

## 🚀 启动方式

### 1. **一键启动所有服务**
```bash
.\start-microservices.bat
```

### 2. **单独启动服务**
```bash
# Eureka Server
.\run-eureka.bat

# Producer Service
.\mvnw spring-boot:run -Pproducer

# Consumer Service  
.\mvnw spring-boot:run -Pconsumer

# Query Service
.\mvnw spring-boot:run -Pquery
```

## 🧪 测试Spring Cloud功能

### 运行测试脚本
```bash
.\test_spring_cloud.bat
```

### 手动测试步骤

#### 1. **验证服务注册**
访问Eureka Dashboard: http://localhost:8761
应该看到三个服务已注册：
- USER-BEHAVIOR-PRODUCER
- USER-BEHAVIOR-CONSUMER  
- USER-BEHAVIOR-QUERY

#### 2. **测试健康检查**
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

#### 3. **测试服务间通信**
```bash
# 通过Feign调用
curl http://localhost:8082/api/query/processing/stats

# 综合系统状态
curl http://localhost:8082/api/query/system/status
```

#### 4. **测试负载均衡**
启动多个Consumer实例，观察Feign客户端轮询调用。

## 📊 监控端点

### 核心监控端点
```bash
# 服务健康状态
GET /actuator/health

# 应用信息
GET /actuator/info

# 性能指标
GET /actuator/metrics

# 环境配置
GET /actuator/env
```

### 应用信息示例
```json
{
  "app": {
    "name": "User Behavior Query Service",
    "description": "Provides REST APIs for querying user behavior data with caching",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

## 🔧 配置说明

### Eureka配置
```properties
# 服务注册地址
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# 使用IP地址注册
eureka.instance.prefer-ip-address=true

# 实例ID配置
eureka.instance.instance-id=${spring.application.name}:${random.value}
```

### Feign配置
```java
@EnableFeignClients  // 启用Feign客户端
@EnableDiscoveryClient  // 启用服务发现
```

## 🎯 高级特性

### 1. **熔断降级**
- 自动fallback机制
- 服务异常时返回默认值
- 保证系统稳定性

### 2. **负载均衡**  
- 客户端负载均衡
- 自动轮询调用
- 健康实例检测

### 3. **服务发现**
- 动态服务发现
- 无需硬编码IP地址
- 自动故障转移

## 🚀 扩展计划

### 可选组件 (未来实现)
- **Spring Cloud Gateway**: API网关
- **Spring Cloud Config**: 配置中心
- **Spring Cloud Sleuth**: 分布式链路跟踪
- **Hystrix Dashboard**: 熔断器监控

## 📈 性能优化

### Eureka优化
```properties
# 关闭自我保护模式(开发环境)
eureka.server.enable-self-preservation=false

# 快速剔除失效实例
eureka.server.eviction-interval-timer-in-ms=5000
```

### Feign优化
- 连接池配置
- 超时时间设置  
- 重试机制

## ✅ 实现总结

**Spring Cloud功能完成度: 90%**

✅ **已实现**:
- 服务注册与发现 (Eureka)
- 服务间通信 (OpenFeign)
- 负载均衡 (Ribbon/LoadBalancer)
- 健康检查 (Actuator)
- 熔断降级 (Fallback)

⚠️ **可选扩展**:
- API网关 (Gateway)
- 配置中心 (Config Server)
- 分布式跟踪 (Sleuth)

您的微服务架构已经具备了企业级的Spring Cloud功能！🎉 