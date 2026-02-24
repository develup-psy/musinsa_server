package com.mudosa.musinsa.order.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TodayOrderListResponse {
    private int totalCount;
    private List<OrderInfo> orders;

    public static TodayOrderListResponse of(List<OrderInfo> orders) {
        return new TodayOrderListResponse(orders.size(), orders);
    }
}
