# Redis 缓存和数据热度识别功能

## 功能概述

本项目实现了基于Redis的缓存机制和数据热度识别功能，用于提升系统读性能和提供热门数据统计。

## 核心功能

### 1. 缓存应用场景

- **用户最近行为缓存**: `user:{userId}:recent_events` - 缓存用户最近N条行为记录
- **事件类型统计**: `stats:event_type_count` - 统计各类事件的发生次数
- **用户活跃度统计**: `stats:user_event_count` - 统计用户的活跃度
- **时间段统计**: `stats:hourly:{yyyy-MM-dd-HH}` 和 `stats:daily:{yyyy-MM-dd}` - 按小时和天统计
- **热门事件缓存**: `stats:top_events` - 缓存热门事件排行榜

### 2. 缓存键设计

```
user:{userId}:recent_events          # 用户最近事件列表 (List)
stats:event_type_count               # 事件类型计数 (Sorted Set)
stats:user_event_count               # 用户活跃度计数 (Sorted Set)
stats:hourly:{yyyy-MM-dd-HH}         # 小时级统计 (Sorted Set)
stats:daily:{yyyy-MM-dd}             # 日级统计 (Sorted Set)
stats:top_events                     # 热门事件缓存 (Sorted Set)
```

### 3. 缓存读写策略

- **写入策略**: 事件处理时同步更新缓存
- **读取策略**: 优先从缓存读取，缓存未命中时返回空结果
- **过期策略**: 
  - 用户最近事件: 24小时TTL
  - 统计数据: 1小时TTL
  - 小时统计: 24小时TTL
  - 日统计: 7天TTL

## API 接口

### 缓存数据查询

```bash
# 获取用户最近事件
GET /api/cache/users/{userId}/recent-events?limit=20

# 获取热门事件类型排行榜
GET /api/cache/stats/top-event-types?topN=10

# 获取最活跃用户排行榜
GET /api/cache/stats/top-active-users?topN=10

# 获取指定时间段统计
GET /api/cache/stats/period/{period}?topN=10
# 示例: /api/cache/stats/period/2024-01-15-14 (小时)
# 示例: /api/cache/stats/period/2024-01-15 (天)
```

### 热度分析

```bash
# 获取实时热点数据报告
GET /api/cache/hot-data/report

# 获取用户行为热度分析
GET /api/cache/hot-data/users/{userId}/heatmap
```

### 系统监控

```bash
# 检查Redis连接状态
GET /api/cache/health/redis

# 获取缓存统计信息
GET /api/cache/stats/cache

# 获取处理统计信息
GET /api/cache/stats/processing
```

## 数据热度识别

### 热度等级定义

- **HOT**: 事件数量 >= 100
- **TRENDING**: 事件数量 >= 50
- **NORMAL**: 事件数量 < 50

### 活跃度等级定义

- **VERY_HIGH**: 事件数量 >= 1000
- **HIGH**: 事件数量 >= 500
- **MEDIUM**: 事件数量 >= 100
- **LOW**: 事件数量 < 100

### 定时分析

系统每5分钟自动执行热点数据分析：
- 分析热门事件类型
- 分析活跃用户
- 分析趋势数据（当前小时 vs 前一小时）

## 配置参数

```properties
# 缓存配置
cache.user.recent.events.size=100        # 用户最近事件缓存数量
cache.user.recent.events.ttl=86400       # 用户最近事件TTL (秒)
cache.stats.ttl=3600                     # 统计数据TTL (秒)

# 热度分析配置
hotdata.analysis.top.events=10           # 热门事件数量
hotdata.analysis.top.users=20            # 活跃用户数量
hotdata.analysis.threshold.hot=100       # 热点阈值
hotdata.analysis.threshold.trending=50   # 趋势阈值

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms
```

## 使用示例

### 1. 启动服务

```bash
# 启动Docker服务 (包含Redis)
docker-compose up -d

# 启动消费者服务
mvn spring-boot:run -Dspring-boot.run.profiles=consumer
```

### 2. 测试缓存功能

```bash
# 运行缓存功能测试
test_cache_functionality.bat
```

### 3. 发送测试事件

```bash
# 发送批量测试事件
test_batch_events.bat
```

## 缓存效果验证

### 缓存命中率监控

通过 `/api/cache/stats/processing` 接口可以查看：
- `receivedCount`: 接收的事件总数
- `cachedCount`: 成功缓存的事件数
- `cacheHitRate`: 缓存命中率

### 性能指标

- **目标缓存命中率**: 80%以上
- **Redis响应时间**: < 10ms
- **热点数据更新频率**: 5分钟

### 监控指标

```bash
# 查看Redis内存使用
redis-cli info memory

# 查看Redis键数量
redis-cli dbsize

# 查看特定键的TTL
redis-cli ttl "user:user123:recent_events"
```

## 故障排除

### 常见问题

1. **Redis连接失败**
   - 检查Docker容器状态: `docker ps`
   - 检查Redis服务: `docker logs redis`

2. **缓存数据为空**
   - 确认事件已发送并处理
   - 检查Redis键是否存在: `redis-cli keys "*"`

3. **缓存命中率低**
   - 检查TTL设置是否合理
   - 分析请求模式是否分散

### 调试命令

```bash
# 连接Redis CLI
docker exec -it redis redis-cli

# 查看所有键
KEYS *

# 查看用户最近事件
LRANGE user:user123:recent_events 0 -1

# 查看事件类型统计
ZREVRANGE stats:event_type_count 0 -1 WITHSCORES

# 查看用户活跃度
ZREVRANGE stats:user_event_count 0 -1 WITHSCORES
```

## 扩展功能

### 未来优化方向

1. **缓存预热**: 系统启动时预加载热点数据
2. **缓存穿透保护**: 使用布隆过滤器防止缓存穿透
3. **分布式缓存**: 支持Redis集群模式
4. **缓存更新策略**: 实现更智能的缓存更新机制
5. **监控告警**: 集成监控系统，实现缓存异常告警 