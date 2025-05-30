-- Script to create the user_behavior_events table

-- Drop table if exists to ensure clean state
DROP TABLE IF EXISTS user_behavior_events;

-- Create the main table for storing user behavior events
CREATE TABLE user_behavior_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    event_data TEXT,
    session_id VARCHAR(100),
    device_info VARCHAR(255),
    ip_address VARCHAR(50),
    processed_time TIMESTAMP NOT NULL,
    topic VARCHAR(100),
    partition INT,
    offset BIGINT,
    
    -- Create indexes for frequently queried fields
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Display created table
SHOW TABLES LIKE 'user_behavior_events';

-- Display table structure
DESCRIBE user_behavior_events; 