-- 验证数据库表是否存在
SHOW TABLES LIKE 'user_behavior_events';

-- 检查表结构
DESCRIBE user_behavior_events;

-- 检查是否有数据
SELECT COUNT(*) AS total_events FROM user_behavior_events;

-- 按事件类型分组查看事件数量
SELECT 
    event_type, 
    COUNT(*) AS event_count,
    MIN(event_time) AS first_event,
    MAX(event_time) AS last_event
FROM 
    user_behavior_events
GROUP BY 
    event_type
ORDER BY 
    event_count DESC;

-- 检查最近10条事件
SELECT 
    id,
    user_id,
    event_type,
    source,
    event_time,
    SUBSTR(event_data, 1, 100) AS event_data_sample,
    session_id,
    device_info,
    ip_address,
    processed_time
FROM 
    user_behavior_events
ORDER BY 
    event_time DESC
LIMIT 10;

-- 检查索引是否存在
SHOW INDEX FROM user_behavior_events; 