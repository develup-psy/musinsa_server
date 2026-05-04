CREATE TABLE IF NOT EXISTS payment_queue_outbox_event (
    payment_queue_outbox_event_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    published_at DATETIME NULL,
    fail_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_queue_outbox_event_id),
    UNIQUE KEY uk_payment_queue_outbox_payment (payment_id),
    KEY idx_payment_queue_outbox_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
