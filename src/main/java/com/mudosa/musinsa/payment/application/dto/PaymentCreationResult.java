package com.mudosa.musinsa.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentCreationResult {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private LocalDateTime createdAt;

    @Builder
    public PaymentCreationResult(Long paymentId, Long orderId, Long userId, LocalDateTime createdAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
