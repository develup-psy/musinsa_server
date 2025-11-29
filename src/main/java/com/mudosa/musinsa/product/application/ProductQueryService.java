package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.application.mapper.ProductQueryMapper;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 조회 전용 서비스. 목록, 상세, 검색 응답을 구성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;

	/**
	 * 검색 조건에 맞는 상품을 조회해 페이지 형태로 반환한다.
	 */
	public ProductSearchResponse searchProducts(ProductSearchCondition condition) {
		// 1. 검색 조건 파싱
		SearchParams params = parseCondition(condition);

		// 2. 키워드 유무에 따라 적절한 검색 메서드 호출
		Page<Product> page = findProducts(params);

		// 3. 응답 DTO 변환
		return ProductQueryMapper.toSearchResponse(page, params.pageable);
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
		List<Category> allCategories = categoryRepository.findAllWithParent();

		List<Category> parentCategories = allCategories.stream()
			.filter(category -> category.getParent() == null)
			.collect(Collectors.toList());

		Map<Long, List<Category>> childrenMap = allCategories.stream()
			.filter(category -> category.getParent() != null)
			.collect(Collectors.groupingBy(category -> category.getParent().getCategoryId()));

		List<CategoryTreeResponse.CategoryNode> categoryNodes = parentCategories.stream()
			.map(parent -> toNode(parent, childrenMap.getOrDefault(parent.getCategoryId(), List.of())))
			.collect(Collectors.toList());

		return new CategoryTreeResponse(categoryNodes);
	}

	// 카테고리와 그 자식 카테고리들을 CategoryNode로 변환한다.
	private CategoryTreeResponse.CategoryNode toNode(Category category, List<Category> children) {
		List<CategoryTreeResponse.CategoryNode> childNodes = children.stream()
			.map(child -> new CategoryTreeResponse.CategoryNode(
				child.getCategoryId(),
				child.getCategoryName(),
				child.buildPath(),
				child.getImageUrl(),
				List.of() // 손주는 없으므로 빈 리스트
			))
			.collect(Collectors.toList());

		return new CategoryTreeResponse.CategoryNode(
			category.getCategoryId(),
			category.getCategoryName(),
			category.buildPath(),
			category.getImageUrl(),
			childNodes
		);
	}

	// 검색 조건을 안전하게 파싱해 내부용 검색 파라미터 객체로 변환한다.
	private SearchParams parseCondition(ProductSearchCondition condition) {
		ProductSearchCondition safeCondition = condition != null ? condition : ProductSearchCondition.builder().build();
		Pageable pageable = ensurePaged(safeCondition.getPageable());
		return new SearchParams(
			safeCondition.getKeyword(),
			safeCondition.getCategoryPaths(),
			safeCondition.getGender(),
			safeCondition.getBrandId(),
			pageable,
			safeCondition.getPriceSort()
		);
	}

	// Pageable이 null이거나 unpaged인 경우 기본 페이지 크기로 변환한다.
	private Pageable ensurePaged(Pageable pageable) {
		if (pageable == null || pageable.isUnpaged()) {
			// 기본 페이지 크기 24로 unpaged 요청을 방어한다.
			return PageRequest.of(0, 24);
		}
		return pageable;
	}

	// 검색 파라미터에 따라 적절한 상품 조회 메서드를 호출한다.
	private Page<Product> findProducts(SearchParams params) {
		if (params.keyword != null && !params.keyword.isBlank()) {
			return productRepository.searchByKeywordWithFilters(
				params.keyword, params.categoryPaths, params.gender, params.brandId, params.priceSort, params.pageable);
		}
		return productRepository.findAllByFiltersWithPagination(
			params.categoryPaths, params.gender, params.brandId, params.priceSort, params.pageable);
	}

	// 상품의 최저가를 계산한다.
	private record SearchParams(
		String keyword,
		List<String> categoryPaths,
		ProductGenderType gender,
		Long brandId,
		Pageable pageable,
		ProductSearchCondition.PriceSort priceSort
	) {
	}
}
