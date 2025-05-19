# 用户行为分析基础环境

此项目提供了一个完整的本地开发环境，包含以下服务：
- Kafka (消息队列)
- MySQL (关系型数据库)
- Redis (缓存/键值存储)

## 前提条件

确保您的系统中已安装：
- Docker
- Docker Compose

## 启动服务

在项目根目录下执行以下命令启动所有服务：

```bash
docker-compose up -d
```

要查看服务日志：

```bash
docker-compose logs -f
```

停止所有服务：

```bash
docker-compose down
```

停止并删除所有数据卷（将丢失所有数据）：

```bash
docker-compose down -v
```

## 服务访问信息

### Kafka
- 内部访问地址: `kafka:9092`
- 外部访问地址: `localhost:29092`
- Zookeeper地址: `localhost:2181`

### MySQL
- 主机: `localhost`
- 端口: `3306`
- 数据库: `userdb`
- 用户名: `user`
- 密码: `password`
- Root密码: `rootpassword`

### Redis
- 主机: `localhost`
- 端口: `6379`
- 无密码（在生产环境中应添加密码）

## 服务说明

### Kafka
Kafka是一个分布式流处理平台，用于实时数据流的处理和传递。在此设置中，Kafka依赖于Zookeeper进行服务发现和配置管理。

### MySQL
MySQL是一个广泛使用的关系型数据库管理系统。当前配置创建了一个初始数据库`userdb`。

### Redis
Redis是一个内存数据结构存储，可用作数据库、缓存和消息中间件。当前配置启用了AOF持久性，以确保数据持久化。

## 注意事项

- 此配置适用于本地开发环境，不建议用于生产环境。
- 默认密码和配置应在生产环境中更改，以提高安全性。
- 数据存储在Docker卷中，除非显式删除，否则数据会持久保存。 