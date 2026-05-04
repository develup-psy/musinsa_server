package com.mudosa.musinsa.payment.domain.model;

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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_queue_outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_queue_outbox_payment", columnNames = "payment_id")
        },
        indexes = {
                @Index(name = "idx_payment_queue_outbox_status_retry", columnList = "status,next_retry_at")
        }
)
public class PaymentQueueOutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_queue_outbox_event_id")
    private Long paymentQueueOutboxEventId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentQueueOutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    private PaymentQueueOutboxEvent(Long paymentId, String orderNo) {
        this.paymentId = paymentId;
        this.orderNo = orderNo;
        this.status = PaymentQueueOutboxEventStatus.PENDING;
        this.retryCount = 0;
    }

    public static PaymentQueueOutboxEvent create(Long paymentId, String orderNo) {
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "paymentId는 필수입니다.");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "orderNo는 필수입니다.");
        }
        return new PaymentQueueOutboxEvent(paymentId, orderNo);
    }

    public void markPublished() {
        this.status = PaymentQueueOutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.nextRetryAt = null;
        this.failReason = null;
    }

    public void markRetryScheduled(String reason, LocalDateTime nextRetryAt) {
        this.status = PaymentQueueOutboxEventStatus.PENDING;
        this.retryCount = Objects.requireNonNullElse(this.retryCount, 0) + 1;
        this.nextRetryAt = nextRetryAt;
        this.failReason = truncate(reason);
    }

    public void markFailed(String reason) {
        this.status = PaymentQueueOutboxEventStatus.FAILED;
        this.nextRetryAt = null;
        this.failReason = truncate(reason);
    }

    public void reopenPending(String reason) {
        this.status = PaymentQueueOutboxEventStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now();
        this.failReason = truncate(reason);
    }

    public boolean isReadyToPublish(LocalDateTime now) {
        if (status != PaymentQueueOutboxEventStatus.PENDING) {
            return false;
        }
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    public boolean isPublished() {
        return this.status == PaymentQueueOutboxEventStatus.PUBLISHED;
    }

    public boolean isFailed() {
        return this.status == PaymentQueueOutboxEventStatus.FAILED;
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
