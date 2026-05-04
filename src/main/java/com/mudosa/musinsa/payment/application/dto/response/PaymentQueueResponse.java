package com.mudosa.musinsa.payment.application.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentQueueResponse {
    private Long ticketId;
    private String status;
    private String message;
    private String orderNo;
}
