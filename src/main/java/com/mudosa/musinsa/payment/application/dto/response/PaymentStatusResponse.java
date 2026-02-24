package com.mudosa.musinsa.payment.application.dto.response;

import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentStatusResponse {

    private PaymentStatus status;
    private Long queuePosition;
    private Long estimatedWaitSeconds;
    private String orderNo;
    private String failReason;

    public static PaymentStatusResponse queued(Long position, Long estimatedWaitSeconds) {
        Long queuePosition = position == null ? null : position + 1;
        return PaymentStatusResponse.builder()
                .status(PaymentStatus.QUEUED)
                .queuePosition(queuePosition)
                .estimatedWaitSeconds(estimatedWaitSeconds)
                .build();
    }

    public static PaymentStatusResponse approved(Payment payment) {
        return PaymentStatusResponse.builder()
                .status(PaymentStatus.APPROVED)
                .orderNo(payment.getOrderNo())
                .build();
    }

    public static PaymentStatusResponse failed(String failReason) {
        return PaymentStatusResponse.builder()
                .status(PaymentStatus.FAILED)
                .failReason(failReason)
                .build();
    }

    public static PaymentStatusResponse pending() {
        return PaymentStatusResponse.builder()
                .status(PaymentStatus.PENDING)
                .build();
    }
}
