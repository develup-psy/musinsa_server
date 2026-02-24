CREATE TABLE IF NOT EXISTS inventory_outbox_event (
    inventory_outbox_event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    product_option_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fail_reason VARCHAR(255) NULL,
    processed_at DATETIME NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inventory_outbox_status_id (status, inventory_outbox_event_id),
    INDEX idx_inventory_outbox_status_retry_id (status, next_retry_at, inventory_outbox_event_id),
    INDEX idx_inventory_outbox_product_option (product_option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
