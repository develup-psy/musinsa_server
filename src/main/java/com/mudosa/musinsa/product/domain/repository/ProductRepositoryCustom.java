package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;

import java.util.List;

// 상품 검색을 위한 커스텀 리포지토리 인터페이스이다.
public interface ProductRepositoryCustom {

    // 필터링 조건값과 커서/limit를 기반으로 상품 목록을 반환한다. (키워드 제외)
    List<ProductSearchResponse.ProductSummary> findAllByFiltersWithCursor(List<String> categoryPaths,
                                                                          ProductGenderType gender,
                                                                          Long brandId,
                                                                          ProductSearchCondition.PriceSort priceSort,
                                                                          Cursor cursor,
                                                                          int limit);

    // 키워드를 기반으로 상품을 검색하고 커서/limit 조건으로 반환한다.
    List<ProductSearchResponse.ProductSummary> searchByKeywordWithFilters(String keyword,
                                                                          List<String> categoryPaths,
                                                                          ProductGenderType gender,
                                                                          Long brandId,
                                                                          ProductSearchCondition.PriceSort priceSort,
                                                                          Cursor cursor,
                                                                          int limit);

    // 커서 값 전달용 DTO
    record Cursor(java.math.BigDecimal price, Long productId) {}
}
