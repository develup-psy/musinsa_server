package com.mudosa.musinsa.product.application.dto;

import java.math.BigDecimal;
import com.mudosa.musinsa.product.domain.model.CartItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장바구니 항목 정보를 반환하는 응답 DTO이다.
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemResponse {

    private Long cartItemId;
    private Long userId;
    private Long productOptionId;
    private Integer quantity;
    private BigDecimal unitPrice;

    public static CartItemResponse from(CartItem cartItem) {
        return CartItemResponse.builder()
                .cartItemId(cartItem.getCartItemId())
                .userId(cartItem.getUser() != null ? cartItem.getUser().getId() : null)
                .productOptionId(cartItem.getProductOption() != null
                ? cartItem.getProductOption().getProductOptionId()
                : null)
            .quantity(cartItem.getQuantity())
            .unitPrice(cartItem.getProductOption() != null && cartItem.getProductOption().getProductPrice() != null
                ? cartItem.getProductOption().getProductPrice().getAmount()
                : null)
            .build();
    }
}
