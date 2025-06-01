-- Complete Data Storage Validation Script
-- 检查数据库连接和表结构
SELECT 'Database Connection Test' as step;
SELECT NOW() as current_time;

-- 检查表是否存在
SELECT 'Table Existence Check' as step;
SHOW TABLES LIKE 'user_behavior_events';

-- 检查表结构
SELECT 'Table Structure Check' as step;
DESCRIBE user_behavior_events;

-- 检查数据计数
SELECT 'Data Count Check' as step;
SELECT COUNT(*) as total_events FROM user_behavior_events;

-- 检查最近的数据（如果有）
SELECT 'Recent Events Check' as step;
SELECT 
    user_id,
    event_type, 
    source,
    event_time,
    processed_time,
    topic,
    `partition`,
    `offset`
FROM user_behavior_events 
ORDER BY processed_time DESC 
LIMIT 5;

-- 检查按事件类型分组的数据
SELECT 'Event Type Distribution' as step;
SELECT 
    event_type,
    COUNT(*) as count
FROM user_behavior_events
GROUP BY event_type
ORDER BY count DESC;

-- 检查字符编码问题
SELECT 'Character Encoding Check' as step;
SELECT 
    user_id,
    event_type,
    HEX(SUBSTRING(event_data, 1, 20)) as hex_sample
FROM user_behavior_events 
WHERE event_data IS NOT NULL
LIMIT 3;

-- 检查索引
SELECT 'Index Check' as step;
SHOW INDEX FROM user_behavior_events; 