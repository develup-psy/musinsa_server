package com.mudosa.musinsa.product.domain.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventory_outbox_event")
public class InventoryOutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_outbox_event_id")
    private Long inventoryOutboxEventId;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private InventoryOutboxEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryOutboxEventStatus status;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    private InventoryOutboxEvent(
            String orderNo,
            Long productOptionId,
            Integer quantity,
            InventoryOutboxEventType eventType
    ) {
        this.orderNo = orderNo;
        this.productOptionId = productOptionId;
        this.quantity = quantity;
        this.eventType = eventType;
        this.status = InventoryOutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = null;
    }

    public static InventoryOutboxEvent create(
            String orderNo,
            Long productOptionId,
            Integer quantity,
            InventoryOutboxEventType eventType
    ) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "orderNo는 필수입니다.");
        }
        if (productOptionId == null) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_ID_REQUIRED);
        }
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
        }
        if (eventType == null) {
            throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE, "eventType은 필수입니다.");
        }

        return new InventoryOutboxEvent(orderNo, productOptionId, quantity, eventType);
    }

    public void markProcessed() {
        this.status = InventoryOutboxEventStatus.PROCESSED;
        this.failReason = null;
        this.processedAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    public void markRetryScheduled(String reason, LocalDateTime nextRetryAt) {
        this.status = InventoryOutboxEventStatus.PENDING;
        this.retryCount = Objects.requireNonNullElse(this.retryCount, 0) + 1;
        this.failReason = truncate(reason);
        this.nextRetryAt = nextRetryAt;
        this.processedAt = null;
    }

    public void markFailed(String reason) {
        this.status = InventoryOutboxEventStatus.FAILED;
        this.failReason = truncate(reason);
        this.processedAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    public boolean isPending() {
        return this.status == InventoryOutboxEventStatus.PENDING;
    }

    public boolean isReadyToProcess(LocalDateTime now) {
        if (!isPending()) {
            return false;
        }
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
