package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.application.mapper.ProductQueryMapper;
import com.mudosa.musinsa.product.infrastructure.cache.CategoryCache;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 조회 전용 서비스. 목록, 상세, 검색 응답을 구성한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductQueryService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final CategoryCache categoryCache;

	/**
	 * 검색 조건에 맞는 상품을 조회해 페이지 형태로 반환한다.
	 */
	public ProductSearchResponse searchProducts(ProductSearchCondition condition) {
		// 1. 검색 조건 파싱
		SearchParams params = parseCondition(condition);

		// 2. 키워드 유무에 따라 적절한 검색 메서드 호출 (요약 DTO)
		List<ProductSearchResponse.ProductSummary> fetchedProducts = findProducts(params);

		boolean hasNext = fetchedProducts.size() > params.limit;
		List<ProductSearchResponse.ProductSummary> products = hasNext ? fetchedProducts.subList(0, params.limit) : fetchedProducts;

		String nextCursor = hasNext && !products.isEmpty()
			? buildCursor(products.get(products.size() - 1), params.priceSort)
			: null;

		// 3. 응답 DTO 반환
		return ProductQueryMapper.toSearchResponse(products, nextCursor, hasNext, null);
	}

	/**
	 * 단일 상품 상세 정보를 조회한다.
	 */
	public ProductDetailResponse getProductDetail(Long productId) {
		// 1. 상품 엔티티 조회 (상품 + 옵션)
		Product product = productRepository.findDetailById(productId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,"해당 상품을 찾을 수 없거나 비활성화된 상품입니다"));

		// 2. 응답 DTO 변환
		return ProductQueryMapper.toProductDetail(product);
	}

	/**
	 * 전체 카테고리를 트리 형태로 반환한다.
	 */
	public CategoryTreeResponse getCategoryTree() {
		CategoryTreeResponse cached = categoryCache.getTree();
		if (cached != null) {
			return cached;
		}

		List<Category> allCategories = categoryRepository.findAllWithParent();

		List<Category> parentCategories = allCategories.stream()
			.filter(category -> category.getParent() == null)
			.collect(Collectors.toList());

		Map<Long, List<Category>> childrenMap = allCategories.stream()
			.filter(category -> category.getParent() != null)
			.collect(Collectors.groupingBy(category -> category.getParent().getCategoryId()));

		List<CategoryTreeResponse.CategoryNode> categoryNodes = parentCategories.stream()
			.map(parent -> toNode(parent, childrenMap.getOrDefault(parent.getCategoryId(), List.of())))
			.collect(Collectors.toUnmodifiableList());

		CategoryTreeResponse tree = CategoryTreeResponse.builder()
			.categories(categoryNodes)
			.build();
		categoryCache.saveTree(tree);
		categoryCache.saveAll(CategoryTreeResponse.flatten(tree));
		return tree;
	}

	// 카테고리와 그 자식 카테고리들을 CategoryNode로 변환한다.
	private CategoryTreeResponse.CategoryNode toNode(Category category, List<Category> children) {
		List<CategoryTreeResponse.CategoryNode> childNodes = children.stream()
			.map(child -> CategoryTreeResponse.CategoryNode.builder()
				.categoryId(child.getCategoryId())
				.categoryName(child.getCategoryName())
				.categoryPath(child.buildPath())
				.imageUrl(child.getImageUrl())
				.children(List.of()) // 손주는 없으므로 빈 리스트
				.build())
			.collect(Collectors.toUnmodifiableList());

		return CategoryTreeResponse.CategoryNode.builder()
			.categoryId(category.getCategoryId())
			.categoryName(category.getCategoryName())
			.categoryPath(category.buildPath())
			.imageUrl(category.getImageUrl())
			.children(childNodes)
			.build();
	}

    

	// 검색 조건을 안전하게 파싱해 내부용 검색 파라미터 객체로 변환한다.
	private SearchParams parseCondition(ProductSearchCondition condition) {
		ProductSearchCondition safeCondition = condition != null ? condition : ProductSearchCondition.builder().build();
		int limit = safeCondition.getLimit();
		ProductRepositoryCustom.Cursor cursor = parseCursor(safeCondition.getCursor(), safeCondition.getPriceSort());
		return new SearchParams(
			safeCondition.getKeyword(),
			safeCondition.getCategoryPaths(),
			safeCondition.getGender(),
			safeCondition.getBrandId(),
			safeCondition.getPriceSort(),
			cursor,
			safeCondition.getCursor(),
			limit
		);
	}

	// 검색 파라미터에 따라 적절한 상품 조회 메서드를 호출한다.
	private List<ProductSearchResponse.ProductSummary> findProducts(SearchParams params) {
		boolean keywordPresent = params.keyword != null && !params.keyword.isBlank();
		if (keywordPresent) {
			return productRepository.searchByKeywordWithFilters(
				params.keyword, params.categoryPaths, params.gender, params.brandId, params.priceSort, params.cursor, params.limit + 1);
		}
		return productRepository.findAllByFiltersWithCursor(
			params.categoryPaths, params.gender, params.brandId, params.priceSort, params.cursor, params.limit + 1);
	}

	private ProductRepositoryCustom.Cursor parseCursor(String cursor, ProductSearchCondition.PriceSort priceSort) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			if (priceSort == null) {
				Long id = Long.parseLong(cursor);
				return new ProductRepositoryCustom.Cursor(null, id);
			}
			String[] parts = cursor.split(":");
			if (parts.length != 2) {
				return null;
			}
			BigDecimal price = new BigDecimal(parts[0]);
			Long id = Long.parseLong(parts[1]);
			return new ProductRepositoryCustom.Cursor(price, id);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String buildCursor(ProductSearchResponse.ProductSummary last, ProductSearchCondition.PriceSort priceSort) {
		if (last == null || last.getProductId() == null) {
			return null;
		}
		if (priceSort == null) {
			return String.valueOf(last.getProductId());
		}
		BigDecimal price = last.getLowestPrice();
		String pricePart = price != null ? price.stripTrailingZeros().toPlainString() : "0";
		return pricePart + ":" + last.getProductId();
	}

	// 상품 검색 파라미터를 담는 내부 클래스 (record 사용 시 IDE 호환 이슈 방지)
	private static class SearchParams {
		private final String keyword;
		private final List<String> categoryPaths;
		private final ProductGenderType gender;
		private final Long brandId;
		private final ProductSearchCondition.PriceSort priceSort;
		private final ProductRepositoryCustom.Cursor cursor;
		private final String rawCursor;
		private final int limit;

		private SearchParams(String keyword, List<String> categoryPaths, ProductGenderType gender, Long brandId,
							 ProductSearchCondition.PriceSort priceSort, ProductRepositoryCustom.Cursor cursor,
							 String rawCursor, int limit) {
			this.keyword = keyword;
			this.categoryPaths = categoryPaths;
			this.gender = gender;
			this.brandId = brandId;
			this.priceSort = priceSort;
			this.cursor = cursor;
			this.rawCursor = rawCursor;
			this.limit = limit;
		}
	}

}
