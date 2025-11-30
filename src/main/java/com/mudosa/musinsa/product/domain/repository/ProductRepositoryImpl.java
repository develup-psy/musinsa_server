package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.mudosa.musinsa.product.domain.model.QProduct.product;

// 상품 검색 조건을 조합해 목록 결과를 반환하는 커스텀 구현체.
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 필터링 조건을 QueryDSL로 조합해 상품 요약 목록을 커서 기반으로 조회한다. (키워드 제외)
    @Override
    public List<ProductSearchResponse.ProductSummary> findAllByFiltersWithCursor(List<String> categoryPaths,
                                                                                 ProductGenderType gender,
                                                                                 Long brandId,
                                                                                 ProductSearchCondition.PriceSort priceSort,
                                                                                 Cursor cursor,
                                                                                 int limit) {
        return baseQuery(categoryPaths, gender, brandId, priceSort, cursor, limit);
    }

    // 키워드 검색 + 필터 조합 (QueryDSL)
    @Override
    public List<ProductSearchResponse.ProductSummary> searchByKeywordWithFilters(String keyword,
                                                                                 List<String> categoryPaths,
                                                                                 ProductGenderType gender,
                                                                                 Long brandId,
                                                                                 ProductSearchCondition.PriceSort priceSort,
                                                                                 Cursor cursor,
                                                                                 int limit) {
        return baseQuery(categoryPaths, gender, brandId, priceSort, cursor, limit, keyword);
    }

    private List<ProductSearchResponse.ProductSummary> baseQuery(List<String> categoryPaths,
                                                                 ProductGenderType gender,
                                                                 Long brandId,
                                                                 ProductSearchCondition.PriceSort priceSort,
                                                                 Cursor cursor,
                                                                 int limit) {
        return baseQuery(categoryPaths, gender, brandId, priceSort, cursor, limit, null);
    }

    private List<ProductSearchResponse.ProductSummary> baseQuery(List<String> categoryPaths,
                                                                 ProductGenderType gender,
                                                                 Long brandId,
                                                                 ProductSearchCondition.PriceSort priceSort,
                                                                 Cursor cursor,
                                                                 int limit,
                                                                 String keyword) {
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(product.isAvailable.isTrue());
        conditions.add(categoryPathsCondition(categoryPaths));
        conditions.add(genderCondition(gender));
        conditions.add(brandCondition(brandId));
        conditions.add(cursorCondition(priceSort, cursor));
        conditions.add(keywordCondition(keyword));

        return queryFactory
            .select(Projections.constructor(
                ProductSearchResponse.ProductSummary.class,
                product.productId,
                product.brand.brandId,
                product.brandName,
                product.productName,
                product.productInfo,
                product.productGenderType.stringValue(),
                product.isAvailable,
                Expressions.nullExpression(Boolean.class), // hasStock 필드는 상품 조회에 필요없음
                product.defaultPrice,
                product.thumbnailImage,
                product.categoryPath
            ))
            .from(product)
            .where(allOf(conditions))
            .orderBy(orderSpecifiers(priceSort))
            .limit(limit)
            .fetch();
    }

    // 카테고리 경로 필터 조건 생성
    private BooleanExpression categoryPathsCondition(List<String> categoryPaths) {
        if (categoryPaths == null || categoryPaths.isEmpty()) {
            return null;
        }
        return product.categoryPath.in(categoryPaths);
    }

    // 성별 필터 조건 생성
    private BooleanExpression genderCondition(ProductGenderType gender) {
        if (gender == null) {
            return null;
        }
        return product.productGenderType.eq(gender);
    }

    // 브랜드 필터 조건 생성
    private BooleanExpression brandCondition(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return product.brand.brandId.eq(brandId);
    }

    // 커서 기반 페이지 조건 생성 (가격 정렬 포함)
    private BooleanExpression cursorCondition(ProductSearchCondition.PriceSort priceSort, Cursor cursor) {
        if (cursor == null || cursor.productId() == null) {
            return null;
        }
        BigDecimal cursorPrice = cursor.price();
        if (priceSort == ProductSearchCondition.PriceSort.HIGHEST && cursorPrice != null) {
            return product.defaultPrice.lt(cursorPrice)
                .or(product.defaultPrice.eq(cursorPrice).and(product.productId.gt(cursor.productId())));
        }
        if (priceSort == ProductSearchCondition.PriceSort.LOWEST && cursorPrice != null) {
            return product.defaultPrice.gt(cursorPrice)
                .or(product.defaultPrice.eq(cursorPrice).and(product.productId.gt(cursor.productId())));
        }
        return product.productId.gt(cursor.productId());
    }

    // 정렬 조건(가격 정렬 우선, 이후 productId) 구성
    private OrderSpecifier<?>[] orderSpecifiers(ProductSearchCondition.PriceSort priceSort) {
        if (priceSort == ProductSearchCondition.PriceSort.HIGHEST) {
            return new OrderSpecifier<?>[]{product.defaultPrice.desc(), product.productId.asc()};
        }
        if (priceSort == ProductSearchCondition.PriceSort.LOWEST) {
            return new OrderSpecifier<?>[]{product.defaultPrice.asc(), product.productId.asc()};
        }
        return new OrderSpecifier<?>[]{product.productId.asc()};
    }

    // null이 아닌 조건만 AND로 묶는다.
    private BooleanExpression allOf(List<BooleanExpression> expressions) {
        return expressions.stream()
            .filter(expr -> expr != null)
            .reduce(BooleanExpression::and)
            .orElse(null);
    }

    // 키워드 LIKE 조건 생성 (상품명/정보/브랜드명/카테고리 경로)
    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String lowered = keyword.toLowerCase();
        return product.productName.lower().contains(lowered)
            .or(product.productInfo.lower().contains(lowered))
            .or(product.brandName.lower().contains(lowered))
            .or(product.categoryPath.lower().contains(lowered));
    }

}
