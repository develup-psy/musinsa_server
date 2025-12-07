package com.mudosa.musinsa.order.application.dto;

import com.mudosa.musinsa.common.vo.Money;

public record ProductOptionWithAvailability(
        Long productOptionId,
        Long productId,
        Boolean isAvailable,
        Money productPrice,
        Integer stockQuantity
) {}
