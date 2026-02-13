package com.mudosa.musinsa.payment.application.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentQueueResponse {
    private Long ticketId;
    private Long queuePosition;
    private Long estimatedWaitSeconds;
    private String status;
    private String orderNo;
}
