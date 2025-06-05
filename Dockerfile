# 使用多阶段构建优化镜像大小
FROM eclipse-temurin:17-jdk-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制Maven包装器和pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 提前下载依赖以利用Docker缓存
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

# 运行时镜像
FROM eclipse-temurin:17-jre-alpine

# 安装必要的运行时工具
RUN apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建非root用户
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser

# 设置工作目录
WORKDIR /app

# 从builder阶段复制jar文件
COPY --from=builder /app/target/*.jar app.jar

# 设置文件权限
RUN chown -R appuser:appuser /app

# 切换到非root用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# JVM优化参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 