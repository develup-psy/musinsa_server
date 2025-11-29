package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

// 상품 검색 조건을 조합해 목록 결과를 반환하는 커스텀 구현체.
@Repository
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    // 필터링 조건을 Criteria API로 조합해 상품 목록을 데이터베이스 레벨에서 필터링, 정렬, 페이징하여 조회한다. (키워드 제외)
    @Override
    public Page<Product> findAllByFiltersWithPagination(List<String> categoryPaths,
                                                      ProductGenderType gender,
                                                      Long brandId,
                                                      ProductSearchCondition.PriceSort priceSort,
                                                      Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> product = cq.from(Product.class);

        List<Predicate> predicates = buildPredicates(cb, product, categoryPaths, gender, brandId);

        // DISTINCT를 사용하면 H2에서 ORDER BY 서브쿼리가 SELECT 목록에 포함되어야 하므로 제거했다.
        cq.select(product);
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // 정렬 조건 적용
        applySorting(cb, cq, product, priceSort, pageable);

        // 페이징 적용
        int totalElements = countTotalElements(cb, categoryPaths, gender, brandId);
        
        jakarta.persistence.TypedQuery<Product> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Product> content = query.getResultList();
        return new PageImpl<>(content, pageable, totalElements);
    }

    // 필터링 조건 빌드 (키워드 제외)
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Product> product,
                                         List<String> categoryPaths, ProductGenderType gender,
                                         Long brandId) {
        List<Predicate> predicates = new ArrayList<>();

        if (gender != null) {
            Expression<ProductGenderType> genderPath = product.get("productGenderType");
            predicates.add(cb.equal(genderPath, gender));
        }

        if (categoryPaths != null && !categoryPaths.isEmpty()) {
            Expression<String> categoryNameExpression = product.get("categoryPath");
            List<Predicate> categoryPredicates = new ArrayList<>();
            for (String categoryPath : categoryPaths) {
                categoryPredicates.add(cb.equal(categoryNameExpression, categoryPath));
            }
            predicates.add(cb.or(categoryPredicates.toArray(new Predicate[0])));
        }

        if (brandId != null) {
            predicates.add(cb.equal(product.get("brand").get("brandId"), brandId));
        }

        // 항상 판매 가능 상품만 조회한다.
        predicates.add(cb.isTrue(product.get("isAvailable")));

        return predicates;
    }

    // 정렬 조건 적용
    private void applySorting(CriteriaBuilder cb, CriteriaQuery<Product> cq, Root<Product> product,
                            ProductSearchCondition.PriceSort priceSort, Pageable pageable) {
        List<Order> orders = new ArrayList<>();

        // 가격 정렬이 있는 경우 가장 먼저 적용
        if (priceSort != null) {
            // 역정규화된 대표 가격 컬럼을 그대로 사용해 정렬
            Expression<java.math.BigDecimal> lowestPriceExpr = product.get("defaultPrice");
            
            if (priceSort == ProductSearchCondition.PriceSort.LOWEST) {
                orders.add(cb.asc(lowestPriceExpr));
            } else {
                orders.add(cb.desc(lowestPriceExpr));
            }
        }

        // Pageable에 포함된 정렬 조건 추가 (가격 정렬이 없는 경우에만)
        if (priceSort == null && pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                Expression<?> expression = product.get(order.getProperty());
                orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
            });
        }

        // 기본 정렬 (상품 ID로 정렬하여 결과 일관성 보장)
        if (orders.isEmpty()) {
            orders.add(cb.asc(product.get("productId")));
        }

        cq.orderBy(orders);
    }

    // 전체 개수 계산
    private int countTotalElements(CriteriaBuilder cb, List<String> categoryPaths,
                                 ProductGenderType gender, Long brandId) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        
        List<Predicate> predicates = buildPredicates(cb, countRoot, categoryPaths, gender, brandId);
        
        countQuery.select(cb.countDistinct(countRoot));
        if (!predicates.isEmpty()) {
            countQuery.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        return entityManager.createQuery(countQuery).getSingleResult().intValue();
    }

    // 키워드 Full-Text Search + 필터링 구현 (QueryDSL로 개선 예정)
    @Override
    public Page<Product> searchByKeywordWithFilters(String keyword,
                                                   List<String> categoryPaths,
                                                   ProductGenderType gender,
                                                   Long brandId,
                                                   ProductSearchCondition.PriceSort priceSort,
                                                   Pageable pageable) {
        // TODO: QueryDSL로 Full-Text Search 구현 필요
        // 현재는 임시로 기본 LIKE 검색으로 구현
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> product = cq.from(Product.class);

        List<Predicate> predicates = new ArrayList<>();

        // 키워드 검색 조건
        if (keyword != null && !keyword.isBlank()) {
            String lowered = "%" + keyword.toLowerCase() + "%";
            Expression<String> namePath = cb.lower(product.get("productName"));
            Expression<String> infoPath = cb.lower(product.get("productInfo"));
            Expression<String> brandNamePath = cb.lower(product.get("brandName"));
            Expression<String> categoryPathExpr = cb.lower(product.get("categoryPath"));

            Predicate nameLike = cb.like(namePath, lowered);
            Predicate infoLike = cb.like(infoPath, lowered);
            Predicate brandLike = cb.like(brandNamePath, lowered);
            Predicate categoryLike = cb.like(categoryPathExpr, lowered);

            predicates.add(cb.or(nameLike, infoLike, brandLike, categoryLike));
        }

        // 필터링 조건 추가
        if (gender != null) {
            Expression<ProductGenderType> genderPath = product.get("productGenderType");
            predicates.add(cb.equal(genderPath, gender));
        }

        if (categoryPaths != null && !categoryPaths.isEmpty()) {
            Expression<String> categoryNameExpression = product.get("categoryPath");
            List<Predicate> categoryPredicates = new ArrayList<>();
            for (String categoryPath : categoryPaths) {
                categoryPredicates.add(cb.equal(categoryNameExpression, categoryPath));
            }
            predicates.add(cb.or(categoryPredicates.toArray(new Predicate[0])));
        }

        if (brandId != null) {
            predicates.add(cb.equal(product.get("brand").get("brandId"), brandId));
        }

        // 항상 판매 가능 상품만 조회
        predicates.add(cb.isTrue(product.get("isAvailable")));

        // 위와 동일한 이유로 DISTINCT 없이 조회한다.
        cq.select(product);
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // 정렬 조건 적용
        applySorting(cb, cq, product, priceSort, pageable);

        // 페이징 적용
        int totalElements = countTotalElementsForKeyword(cb, keyword, categoryPaths, gender, brandId);
        
        jakarta.persistence.TypedQuery<Product> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Product> content = query.getResultList();
        return new PageImpl<>(content, pageable, totalElements);
    }

    // 키워드 검색용 전체 개수 계산
    private int countTotalElementsForKeyword(CriteriaBuilder cb, String keyword,
                                            List<String> categoryPaths, ProductGenderType gender, Long brandId) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        
        List<Predicate> predicates = new ArrayList<>();

        // 키워드 검색 조건
        if (keyword != null && !keyword.isBlank()) {
            String lowered = "%" + keyword.toLowerCase() + "%";
            Expression<String> namePath = cb.lower(countRoot.get("productName"));
            Expression<String> infoPath = cb.lower(countRoot.get("productInfo"));
            Expression<String> brandNamePath = cb.lower(countRoot.get("brandName"));
            Expression<String> categoryPathExpr = cb.lower(countRoot.get("categoryPath"));

            Predicate nameLike = cb.like(namePath, lowered);
            Predicate infoLike = cb.like(infoPath, lowered);
            Predicate brandLike = cb.like(brandNamePath, lowered);
            Predicate categoryLike = cb.like(categoryPathExpr, lowered);

            predicates.add(cb.or(nameLike, infoLike, brandLike, categoryLike));
        }

        // 필터링 조건
        if (gender != null) {
            predicates.add(cb.equal(countRoot.get("productGenderType"), gender));
        }

        if (categoryPaths != null && !categoryPaths.isEmpty()) {
            Expression<String> categoryNameExpression = countRoot.get("categoryPath");
            List<Predicate> categoryPredicates = new ArrayList<>();
            for (String categoryPath : categoryPaths) {
                categoryPredicates.add(cb.equal(categoryNameExpression, categoryPath));
            }
            predicates.add(cb.or(categoryPredicates.toArray(new Predicate[0])));
        }

        if (brandId != null) {
            predicates.add(cb.equal(countRoot.get("brand").get("brandId"), brandId));
        }

        predicates.add(cb.isTrue(countRoot.get("isAvailable")));
        
        countQuery.select(cb.countDistinct(countRoot));
        if (!predicates.isEmpty()) {
            countQuery.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        return entityManager.createQuery(countQuery).getSingleResult().intValue();
    }
}
