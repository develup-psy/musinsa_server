package com.mudosa.musinsa.order.application.dto;

import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.querydsl.core.annotations.QueryProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDetail(String orderNo, OrderStatus orderStatus, LocalDateTime registeredAt, BigDecimal totalPrice,
                          Long productOptionId, String brandName, String productName, BigDecimal itemAmount,
                          Integer quantity, String imageUrl, String size, String color) {
    @QueryProjection
    public OrderDetail {
    }
}
