package com.mudosa.musinsa.payment.application.dto.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQueueMessage {
    private Long outboxEventId;
    private Long paymentId;
    private String orderNo;
}
