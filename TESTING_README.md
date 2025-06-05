# 用户行为数据收集系统 - 测试文档

## 概述

本文档描述了用户行为数据收集系统的完整测试策略，包括单元测试、集成测试、性能测试和持续集成配置。

## 测试架构

### 测试分层

```
┌─────────────────────────────────────┐
│           性能测试                    │
│    (Performance Tests)              │
├─────────────────────────────────────┤
│           集成测试                    │
│    (Integration Tests)              │
├─────────────────────────────────────┤
│           单元测试                    │
│      (Unit Tests)                   │
└─────────────────────────────────────┘
```

### 测试覆盖范围

- **单元测试**: 覆盖所有核心业务逻辑
- **集成测试**: 覆盖端到端数据流
- **性能测试**: 验证系统在高负载下的表现
- **代码覆盖率**: 目标 ≥ 70%

## 测试类型详解

### 1. 单元测试 (Unit Tests)

#### 覆盖模块
- **DataCollectionController**: HTTP接口测试
- **UserBehaviorService**: Kafka消息发送测试
- **UserBehaviorProcessingService**: 事件处理逻辑测试
- **UserBehaviorQueryService**: 缓存读取逻辑测试

#### 测试技术栈
- **JUnit 5**: 测试框架
- **Mockito**: Mock框架
- **MockMvc**: Web层测试
- **@WebMvcTest**: 控制器切片测试
- **@ExtendWith(MockitoExtension.class)**: Mockito集成

#### 关键测试场景
```java
// 控制器测试示例
@Test
@DisplayName("成功接收单个事件 - 非关键事件")
void collectEvent_NonCriticalEvent_ShouldReturnAccepted() {
    // Given: 准备测试数据
    // When: 发送HTTP请求
    // Then: 验证响应和服务调用
}

// 服务层测试示例
@Test
@DisplayName("缓存命中时直接返回缓存数据")
void getUserEvents_CacheHit_ShouldReturnFromCache() {
    // Given: Mock缓存命中
    // When: 调用查询方法
    // Then: 验证只访问缓存，不查询数据库
}
```

### 2. 集成测试 (Integration Tests)

#### 测试环境
- **TestContainers**: 容器化测试环境
  - MySQL 8.0
  - Redis 7
  - Kafka (Confluent Platform)
- **Spring Boot Test**: 完整应用上下文
- **TestRestTemplate**: HTTP客户端测试

#### 端到端测试流程
```
HTTP请求 → Controller → Service → Kafka → Consumer → Database
    ↓
验证数据库中的最终状态
```

#### 关键测试场景
- 单个事件处理流程
- 批量事件处理
- 关键事件同步处理
- 错误处理和恢复
- 系统健康检查

### 3. 性能测试 (Performance Tests)

#### 测试指标
- **吞吐量**: requests/second
- **响应时间**: 平均/P95/P99
- **并发处理**: 并发用户数
- **资源使用**: 内存/CPU使用率

#### 测试场景
```java
@Test
@DisplayName("单个事件API并发性能测试")
void concurrentSingleEventTest() {
    // 50个并发用户，每用户20个请求
    // 验证: 成功率≥95%, 响应时间≤1秒, 吞吐量≥10 req/s
}
```

#### 性能基准
- **单个事件API**: 
  - 成功率 ≥ 95%
  - 平均响应时间 ≤ 1秒
  - 吞吐量 ≥ 10 requests/second

- **批量事件API**:
  - 成功率 ≥ 95%
  - 平均响应时间 ≤ 5秒
  - 处理速度 ≥ 100 events/second

## 运行测试

### 本地运行

#### 1. 使用脚本运行 (推荐)
```bash
# 给脚本执行权限
chmod +x run-tests.sh

# 运行单元测试
./run-tests.sh unit

# 运行集成测试
./run-tests.sh integration

# 运行性能测试
./run-tests.sh performance

# 运行代码覆盖率测试
./run-tests.sh coverage

# 运行所有测试
./run-tests.sh all

# 清理测试环境
./run-tests.sh cleanup
```

#### 2. 使用Maven命令
```bash
# 单元测试
mvn clean test -Dspring.profiles.active=test

# 集成测试
mvn clean verify -Dspring.profiles.active=integration-test

# 性能测试
mvn clean test -Dtest="*PerformanceTest" -Dspring.profiles.active=performance-test

# 代码覆盖率
mvn clean verify jacoco:report
```

### 环境要求

#### 单元测试
- Java 17+
- Maven 3.6+

#### 集成测试
- Java 17+
- Maven 3.6+
- Docker (用于TestContainers)

#### 性能测试
- Java 17+
- Maven 3.6+
- 充足的系统资源 (建议8GB+ RAM)

## 持续集成 (CI/CD)

### GitHub Actions 工作流

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  unit-tests:        # 单元测试
  integration-tests: # 集成测试
  code-coverage:     # 代码覆盖率
  performance-tests: # 性能测试 (仅main分支)
  security-scan:     # 安全扫描
  build-and-package: # 构建打包
  docker-build:      # Docker镜像构建
```

### CI流程特点
- **并行执行**: 单元测试和安全扫描并行运行
- **依赖管理**: 集成测试依赖单元测试通过
- **条件执行**: 性能测试仅在main分支执行
- **失败快速**: 任何测试失败立即停止流水线
- **制品管理**: 自动上传测试报告和覆盖率报告

## 测试配置

### 配置文件结构
```
src/test/resources/
├── application-test.yml              # 单元测试配置
├── application-integration-test.yml  # 集成测试配置
└── application-performance-test.yml  # 性能测试配置
```

### 关键配置差异

| 配置项 | 单元测试 | 集成测试 | 性能测试 |
|--------|----------|----------|----------|
| 数据库 | H2内存 | MySQL容器 | H2内存 |
| Redis | 嵌入式 | Redis容器 | 本地Redis |
| Kafka | 嵌入式 | Kafka容器 | 本地Kafka |
| 批处理大小 | 5 | 10 | 100 |
| 日志级别 | DEBUG | INFO | WARN |

## 测试数据管理

### 测试数据策略
- **单元测试**: 使用Builder模式创建测试数据
- **集成测试**: 每个测试前清理数据库
- **性能测试**: 动态生成大量测试数据

### 测试数据示例
```java
// 使用Builder模式创建测试数据
UserBehaviorEvent testEvent = UserBehaviorEvent.builder()
    .userId("test-user-123")
    .eventType("PAGE_VIEW")
    .source("web")
    .sessionId("session-123")
    .deviceInfo("Chrome/Windows")
    .eventTime(LocalDateTime.now())
    .build();
```

## 代码覆盖率

### 覆盖率目标
- **总体覆盖率**: ≥ 70%
- **核心业务逻辑**: ≥ 85%
- **控制器层**: ≥ 90%
- **服务层**: ≥ 80%

### 覆盖率报告
```bash
# 生成覆盖率报告
mvn jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

### JaCoCo配置
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.70</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

## 最佳实践

### 测试编写原则
1. **AAA模式**: Arrange, Act, Assert
2. **单一职责**: 每个测试只验证一个功能点
3. **独立性**: 测试之间不应有依赖关系
4. **可重复性**: 测试结果应该是确定的
5. **快速反馈**: 单元测试应该快速执行

### 命名规范
```java
// 测试方法命名: methodName_scenario_expectedBehavior
@Test
void collectEvent_ValidEvent_ShouldReturnAccepted() { }

@Test
void getUserEvents_CacheHit_ShouldReturnFromCache() { }

@Test
void processEvent_JsonProcessingException_ShouldHandleGracefully() { }
```

### Mock使用指南
```java
// 1. 使用@Mock注解
@Mock
private UserBehaviorService userBehaviorService;

// 2. 配置Mock行为
when(userBehaviorService.sendSynchronously(any(), eq(5000L)))
    .thenReturn(true);

// 3. 验证Mock调用
verify(userBehaviorService, times(1))
    .sendUserBehaviorEvent(any(UserBehaviorEvent.class));
```

## 故障排查

### 常见问题

#### 1. TestContainers启动失败
```bash
# 检查Docker状态
docker info

# 清理Docker资源
docker system prune -f
```

#### 2. 集成测试超时
```yaml
# 增加超时时间
spring:
  kafka:
    consumer:
      properties:
        session.timeout.ms: 30000
```

#### 3. 内存不足
```bash
# 增加JVM内存
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256m"
```

### 调试技巧
```java
// 1. 使用@Slf4j记录调试信息
@Slf4j
class MyTest {
    @Test
    void testMethod() {
        log.debug("Test data: {}", testData);
    }
}

// 2. 使用Awaitility等待异步操作
await()
    .atMost(Duration.ofSeconds(30))
    .pollInterval(Duration.ofSeconds(1))
    .untilAsserted(() -> {
        assertEquals(expected, actual);
    });
```

## 性能监控

### 关键指标
- **响应时间分布**: P50, P95, P99
- **吞吐量**: TPS (Transactions Per Second)
- **错误率**: 4xx/5xx错误比例
- **资源使用**: CPU, 内存, 网络IO

### 监控工具
- **JVM监控**: JVisualVM, JProfiler
- **应用监控**: Spring Boot Actuator
- **容器监控**: Docker stats

## 总结

本测试体系确保了用户行为数据收集系统的质量和可靠性：

1. **全面覆盖**: 从单元到集成到性能的完整测试金字塔
2. **自动化**: CI/CD流水线自动执行所有测试
3. **质量保证**: 70%+代码覆盖率要求
4. **性能验证**: 明确的性能基准和监控
5. **易于维护**: 清晰的测试结构和文档

通过这套测试体系，我们可以：
- 快速发现和修复问题
- 确保代码质量
- 验证系统性能
- 支持持续交付
- 降低生产环境风险 