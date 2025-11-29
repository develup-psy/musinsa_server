package com.mudosa.musinsa.order.application.dto.response;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreateResponse {
    private Long orderId;
    private String orderNo;

    @Builder
    private OrderCreateResponse(Long orderId, String orderNo) {
        this.orderId = orderId;
        this.orderNo = orderNo;
    }

    public static OrderCreateResponse of(Long orderId, String orderNo) {
        return OrderCreateResponse
                .builder()
                        .orderId(orderId)
                        .orderNo(orderNo)
                        .build();
    }
}
