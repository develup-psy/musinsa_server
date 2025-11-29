package com.mudosa.musinsa.product.application.dto;

import java.math.BigDecimal;
import com.mudosa.musinsa.product.domain.model.CartItem;
import com.mudosa.musinsa.product.domain.model.Image;

import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장바구니 목록 조회에 필요한 상품 상세 정보를 반환하는 DTO이다.
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemDetailResponse {

    private Long cartItemId;
    private Long userId;
    private Long productId;
    private Long productOptionId;
    private String productName;
    private String productInfo;
    private String brandName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer stockQuantity;
    private Boolean hasStock;
    private String thumbnailUrl;
    private List<OptionValueSummary> optionValues;

    public List<OptionValueSummary> getOptionValues() {
        return optionValues != null ? optionValues : Collections.emptyList();
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    public static class OptionValueSummary {
        private Long optionValueId;
        private String optionName;
        private String optionValue;
    }

    public static CartItemDetailResponse from(CartItem cartItem) {
        ProductOption productOption = cartItem.getProductOption();
        Product product = Optional.ofNullable(productOption)
            .map(ProductOption::getProduct)
            .orElse(null);

        Integer stockQuantity = null;
        Boolean hasStock = null;
        if (productOption != null && productOption.getInventory() != null
            && productOption.getInventory().getStockQuantity() != null) {
            stockQuantity = productOption.getInventory().getStockQuantity().getValue();
            hasStock = stockQuantity > 0;
        }

        String thumbnailUrl = Optional.ofNullable(product)
            .map(Product::getImages)
            .orElse(Collections.emptyList())
            .stream()
            .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
            .map(Image::getImageUrl)
            .findFirst()
            .orElse(null);

        List<OptionValueSummary> optionSummaries = Optional.ofNullable(productOption)
            .map(po -> po.getProductOptionValues())
            .orElse(Collections.emptyList())
            .stream()
            .map(mapping -> {
                OptionValue optionValue = mapping.getOptionValue();
                return OptionValueSummary.builder()
                        .optionValueId(optionValue != null ? optionValue.getOptionValueId() : null)
                        .optionName(optionValue != null ? optionValue.getOptionName() : null)
                        .optionValue(optionValue != null ? optionValue.getOptionValue() : null)
                        .build();
            })
            .collect(Collectors.toList());

        java.math.BigDecimal unitAmount = productOption.getProductPrice() != null
            ? productOption.getProductPrice().getAmount()
            : null;
            
        return CartItemDetailResponse.builder()
                .cartItemId(cartItem.getCartItemId())
                .userId(cartItem.getUser() != null ? cartItem.getUser().getId() : null)
                .productId(product != null ? product.getProductId() : null)
                .productOptionId(productOption != null ? productOption.getProductOptionId() : null)
                .productName(product != null ? product.getProductName() : null)
                .productInfo(product != null ? product.getProductInfo() : null)
                .brandName(product != null ? product.getBrandName() : null)
                .quantity(cartItem.getQuantity())
                .unitPrice(unitAmount)
                .stockQuantity(stockQuantity)
                .hasStock(hasStock)
                .thumbnailUrl(thumbnailUrl)
                .optionValues(optionSummaries)
                .build();
    }
}
