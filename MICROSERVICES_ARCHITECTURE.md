# 微服务架构设计文档

## 🏗️ 系统架构概览

本项目采用微服务架构，将用户行为数据处理系统拆分为以下独立服务：

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   数据收集服务    │    │   事件处理服务    │    │   数据查询服务    │
│   (Producer)    │    │   (Consumer)    │    │    (Query)     │
│   Port: 8080    │    │   Port: 8081    │    │   Port: 8082   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│      Kafka      │    │     MySQL       │    │     Redis       │
│   消息队列       │    │    持久化存储    │    │    缓存存储      │
│   Port: 29092   │    │   Port: 3306    │    │   Port: 6379    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📋 服务边界定义

### 1. 数据收集服务 (Producer Service)
**职责**: 接收用户行为数据并发送到Kafka
- **端口**: 8080
- **Profile**: `default` / `producer`
- **主要功能**:
  - 接收RESTful API请求
  - 数据验证和预处理
  - 路由到不同Kafka Topic
  - 提供数据收集统计

**核心API**:
```
POST /api/events              # 发送单个事件
POST /api/events/batch        # 批量发送事件
GET  /api/metrics             # 获取发送统计
```

### 2. 事件处理服务 (Consumer Service)
**职责**: 消费Kafka消息，处理并存储到MySQL和Redis
- **端口**: 8081
- **Profile**: `consumer`
- **主要功能**:
  - 消费Kafka消息
  - 批量处理和存储到MySQL
  - 实时更新Redis缓存
  - 数据热度分析
  - 定时统计任务

**核心API**:
```
GET /api/cache/health/redis           # Redis健康检查
GET /api/cache/stats/cache            # 缓存统计
GET /api/cache/hot-data/report        # 热点数据报告
GET /api/cache/stats/processing       # 处理统计
```

### 3. 数据查询服务 (Query Service)
**职责**: 提供数据查询RESTful API，实现缓存Aside模式
- **端口**: 8082
- **Profile**: `query`
- **主要功能**:
  - 用户行为数据查询
  - 统计数据查询
  - 缓存Aside模式实现
  - 高并发查询处理
  - API性能监控

**核心API**:
```
GET /api/query/users/{id}/events              # 用户行为查询
GET /api/query/stats/top-event-types          # 热门事件排行
GET /api/query/stats/user/{id}/summary        # 用户概要统计
GET /api/query/stats/top-active-users         # 活跃用户排行
GET /api/query/events                          # 分页查询所有事件
GET /api/query/health                          # 服务健康检查
```

## 🔧 技术栈

| 服务 | 技术栈 | 说明 |
|------|--------|------|
| 数据收集服务 | Spring Boot, Kafka Producer | 轻量级，专注数据接收 |
| 事件处理服务 | Spring Boot, Kafka Consumer, JPA, Redis | 数据处理和缓存管理 |
| 数据查询服务 | Spring Boot, JPA, Redis, 线程池 | 查询优化和并发处理 |

## 🚀 部署和运行

### 前置条件
1. 启动基础设施：
```bash
docker-compose up -d
```

### 启动服务

#### 方式一：使用Maven Profile
```bash
# 启动数据收集服务
mvn spring-boot:run -Dspring-boot.run.profiles=default

# 启动事件处理服务  
mvn spring-boot:run -Dspring-boot.run.profiles=consumer

# 启动数据查询服务
mvn spring-boot:run -Dspring-boot.run.profiles=query
```

#### 方式二：使用启动脚本
```bash
run-producer.bat    # 8080端口
run-consumer.bat    # 8081端口
run-query.bat       # 8082端口
```

#### 方式三：使用JAR包
```bash
java -jar target/user_behaviour-0.0.1-SNAPSHOT.jar --spring.profiles.active=default
java -jar target/user_behaviour-0.0.1-SNAPSHOT.jar --spring.profiles.active=consumer  
java -jar target/user_behaviour-0.0.1-SNAPSHOT.jar --spring.profiles.active=query
```

## 📊 缓存Aside模式实现

查询服务实现了完整的缓存Aside模式：

1. **读取流程**:
   ```
   查询请求 → 检查Redis缓存 → 缓存命中返回 / 缓存未命中查询MySQL → 回填缓存 → 返回结果
   ```

2. **写入流程**:
   ```
   事件处理服务 → 写入MySQL → 同步更新Redis缓存
   ```

3. **缓存键设计**:
   ```
   query:user:{userId}:events:{limit}     # 用户事件缓存
   query:user:{userId}:summary            # 用户概要缓存
   stats:event_type_count                 # 事件类型统计
   stats:user_event_count                 # 用户活跃度统计
   ```

## 🔒 安全与并发

### 并发处理
- **线程池配置**: 核心10线程，最大50线程
- **连接池**: HikariCP连接池，最大20连接
- **超时设置**: 30秒API超时
- **拒绝策略**: CallerRunsPolicy

### 安全措施（可扩展）
- **API限流**: 支持每分钟1000请求限制
- **API密钥**: 可配置API密钥验证
- **输入验证**: 请求参数验证和长度限制

## 📈 监控和测试

### 性能测试脚本
```bash
test_query_service.bat          # 功能测试
test_concurrent_queries.bat     # 并发测试
test_cache_functionality.bat    # 缓存测试
```

### 监控端点
```
GET /actuator/health            # 健康检查
GET /actuator/metrics           # 性能指标
GET /api/query/health           # 服务状态
```

## 🎯 API使用示例

### 1. 发送事件
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "eventType": "PAGE_VIEW", 
    "source": "web_app",
    "eventTime": "2024-01-15T10:30:00"
  }'
```

### 2. 查询用户事件
```bash
curl "http://localhost:8082/api/query/users/user123/events?limit=10"
```

### 3. 获取热门事件
```bash
curl "http://localhost:8082/api/query/stats/top-event-types?topN=5"
```

### 4. 用户行为概要
```bash
curl "http://localhost:8082/api/query/stats/user/user123/summary"
```

## 🔄 数据流转

```
用户行为数据 → 数据收集服务(8080) → Kafka → 事件处理服务(8081) → MySQL + Redis
                                                                              ↓
                                                        数据查询服务(8082) ← Redis/MySQL
```

## 📝 配置说明

### 环境配置文件
- `application.properties` - 数据收集服务配置
- `application-consumer.properties` - 事件处理服务配置  
- `application-query.properties` - 数据查询服务配置

### 关键配置项
```properties
# 缓存配置
query.cache.enable=true
query.cache.ttl=3600

# 线程池配置
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=50

# 连接池配置
spring.datasource.hikari.maximum-pool-size=20
```

## 🚧 扩展计划

1. **服务发现**: 集成Eureka/Consul
2. **API网关**: 集成Spring Cloud Gateway
3. **链路追踪**: 集成Sleuth/Zipkin
4. **熔断器**: 集成Hystrix/Resilience4j
5. **配置中心**: 集成Spring Cloud Config

这个微服务架构提供了良好的可扩展性、高可用性和性能表现，满足了用户行为数据处理的所有需求。 