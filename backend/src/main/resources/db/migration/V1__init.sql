CREATE TABLE IF NOT EXISTS events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    source VARCHAR(50) NOT NULL,
    schema_version INT NOT NULL,
    page VARCHAR(255) NULL,
    product_id VARCHAR(64) NULL,
    metadata JSON NULL,
    kafka_partition INT NULL,
    kafka_offset BIGINT NULL,
    CONSTRAINT uk_events_event_id UNIQUE (event_id),
    INDEX idx_events_occurred_at (occurred_at DESC),
    INDEX idx_events_type_occurred (event_type, occurred_at DESC),
    INDEX idx_events_client_occurred (client_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_agg_minute (
    bucket_start DATETIME(6) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (bucket_start, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS client_stats (
    client_id VARCHAR(64) NOT NULL PRIMARY KEY,
    total_events BIGINT NOT NULL DEFAULT 1,
    first_seen DATETIME(6) NOT NULL,
    last_seen DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS page_stats (
    page VARCHAR(255) NOT NULL PRIMARY KEY,
    view_count BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS failed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NULL,
    payload TEXT NOT NULL,
    error_class VARCHAR(255) NULL,
    error_message TEXT NULL,
    original_topic VARCHAR(100) NOT NULL,
    original_partition INT NOT NULL,
    original_offset BIGINT NOT NULL,
    failed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_failed_events_record UNIQUE (original_topic, original_partition, original_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
