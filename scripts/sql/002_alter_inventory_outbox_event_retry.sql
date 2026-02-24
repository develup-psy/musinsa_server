ALTER TABLE inventory_outbox_event
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0 AFTER processed_at,
    ADD COLUMN IF NOT EXISTS next_retry_at DATETIME NULL AFTER retry_count;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inventory_outbox_event'
      AND index_name = 'idx_inventory_outbox_status_retry_id'
);
SET @idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_inventory_outbox_status_retry_id ON inventory_outbox_event (status, next_retry_at, inventory_outbox_event_id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
