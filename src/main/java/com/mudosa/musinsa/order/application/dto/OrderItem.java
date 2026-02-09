package com.mudosa.musinsa.order.application.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
public class OrderItem {
    Long productOptionId;
    String brandName;
    String productOptionName;
    BigDecimal amount;
    Integer quantity;
    String imageUrl;
    Map<String, String> options;

    @QueryProjection
    public OrderItem(Long productOptionId, String brandName, String productOptionName,
                     BigDecimal amount, Integer quantity, String imageUrl) {
        this.productOptionId = productOptionId;
        this.brandName = brandName;
        this.productOptionName = productOptionName;
        this.amount = amount;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.options = new LinkedHashMap<>();
    }

    public void applyOptions(Map<String, String> options) {
        this.options = options != null ? options : Map.of();
    }

    public static OrderItem toOrderItem(OrderDetail flatDto) {
        return new OrderItem(
                flatDto.productOptionId(),
                flatDto.brandName(),
                flatDto.productName(),
                flatDto.itemAmount(),
                flatDto.quantity(),
                flatDto.imageUrl());
    }
}
