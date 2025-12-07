package com.mudosa.musinsa.order.application.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class OrderItem {
    Long productOptionId;
    String brandName;
    String productOptionName;
    BigDecimal amount;
    Integer quantity;
    String imageUrl;
    String size;
    String color;

    @QueryProjection
    public OrderItem(Long productOptionId, String brandName, String productOptionName,
                     BigDecimal amount, Integer quantity, String imageUrl, String size, String color) {
        this.productOptionId = productOptionId;
        this.brandName = brandName;
        this.productOptionName = productOptionName;
        this.amount = amount;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.size = size;
        this.color = color;
    }
}
