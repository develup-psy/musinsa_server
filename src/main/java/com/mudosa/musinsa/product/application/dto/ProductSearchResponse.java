package com.mudosa.musinsa.product.application.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

// 상품 목록 조회 응답을 표현하는 DTO이다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {

    private List<ProductSummary> products;
    private String nextCursor;
    private boolean hasNext;
    private Long totalCount; // 무한스크롤에서는 기본 null (선택적)

    // 조회된 상품 요약 목록을 NULL 안전하게 반환한다.
    public List<ProductSummary> getProducts() {
        return products != null ? products : Collections.emptyList();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private Long productId;
        private Long brandId;
        private String brandName;
        private String productName;
        private String productInfo;
        private String productGenderType;
        private Boolean isAvailable;
        private Boolean hasStock;
        private BigDecimal lowestPrice;
        private String thumbnailUrl;
        private String categoryPath;
    }
}
