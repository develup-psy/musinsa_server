package com.mudosa.musinsa.product.application.dto;

import com.mudosa.musinsa.product.application.dto.ProductSearchCondition.PriceSort;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
// 검색 요청 파라미터를 서비스 조건으로 변환하는 DTO이다.
public class ProductSearchRequest {

    private String keyword;
    private List<String> categoryPaths;
    private String gender;
    private Long brandId;
    private String priceSort;
    private String cursor;
    private Integer limit;

    // 요청 값을 ProductSearchCondition으로 변환한다.
    public ProductSearchCondition toCondition() {
        ProductGenderType genderType = parseGender();
        PriceSort sort = parsePriceSort();

        return ProductSearchCondition.builder()
                .keyword(keyword)
                .categoryPaths(categoryPaths != null ? categoryPaths : Collections.emptyList())
                .gender(genderType)
                .brandId(brandId)
                .priceSort(sort)
                .cursor(cursor)
                .limit(limit)
                .build();
    }

    // 문자열 성별 값을 ENUM으로 변환한다.
    private ProductGenderType parseGender() {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return ProductGenderType.valueOf(gender.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // 문자열 가격 정렬 값을 ENUM으로 변환한다.
    private PriceSort parsePriceSort() {
        if (priceSort == null || priceSort.isBlank()) {
            return null;
        }
        try {
            return PriceSort.valueOf(priceSort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // 카테고리 경로 목록을 defensive copy하여 저장한다.
    public void setCategoryPaths(List<String> categoryPaths) {
        this.categoryPaths = categoryPaths != null ? new ArrayList<>(categoryPaths) : new ArrayList<>();
    }
}
