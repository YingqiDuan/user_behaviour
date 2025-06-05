-- 用户行为数据表初始化脚本
CREATE DATABASE IF NOT EXISTS userdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE userdb;

-- 用户行为事件表
CREATE TABLE IF NOT EXISTS user_behavior_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    session_id VARCHAR(100),
    device_info TEXT,
    ip_address VARCHAR(45),
    event_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_time (event_time),
    INDEX idx_user_event_time (user_id, event_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户行为统计表
CREATE TABLE IF NOT EXISTS user_behavior_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_count BIGINT DEFAULT 0,
    last_event_time TIMESTAMP,
    stat_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_user_event_date (user_id, event_type, stat_date),
    INDEX idx_stat_date (stat_date),
    INDEX idx_event_type_date (event_type, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建数据库用户和权限
CREATE USER IF NOT EXISTS 'user'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON userdb.* TO 'user'@'%';
FLUSH PRIVILEGES; 