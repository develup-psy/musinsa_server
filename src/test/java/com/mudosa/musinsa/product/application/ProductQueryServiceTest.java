package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandMember;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import com.mudosa.musinsa.product.application.dto.ProductCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.OptionValueRepository;
import com.mudosa.musinsa.product.domain.model.OptionValue;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductQueryService 테스트")
@Transactional
public class ProductQueryServiceTest extends ServiceConfig {

	@Autowired
	private ProductQueryService sut;
	@Autowired
	private ProductCommandService productCommandService;
	@Autowired
	private BrandRepository brandRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private OptionValueRepository optionValueRepository;

	private Long userId;
	private Long brandId;
	private List<Long> optionValueIds;
	private String topsCategoryPath;
	private String bottomsCategoryPath;

	@BeforeEach
	void setUp() {
		userId = 1L;
		brandId = saveBrandWithMember(userId).getBrandId();
		topsCategoryPath = saveCategoryPath("상의", "티셔츠").buildPath();
		bottomsCategoryPath = saveCategoryPath("하의", "바지").buildPath();
		optionValueIds = List.of(
			saveOptionValue("사이즈", "L").getOptionValueId(),
			saveOptionValue("색상", "블랙").getOptionValueId()
		);
	}

	@Test
	@DisplayName("조건 없이 조회하면 전체 목록이 반환된다.")
	void searchProductsWithoutCondition() {
		// given
		productCommandService.createProduct(
			createProductRequest("블랙 티셔츠", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("화이트 바지", bottomsCategoryPath), brandId, userId);

		// when
		ProductSearchResponse response = sut.searchProducts(null);

		// then
		assertThat(response.getTotalElements()).isEqualTo(2);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactlyInAnyOrder("블랙 티셔츠", "화이트 바지");
	}

	@Test
	@DisplayName("키워드 없으면 필터만으로 검색된다.")
	void searchProductsWithFiltersOnly() {
		// given
		productCommandService.createProduct(
			createProductRequest("블랙 티셔츠", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("화이트 바지", bottomsCategoryPath), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.categoryPaths(List.of(bottomsCategoryPath))
			.gender(ProductGenderType.ALL)
			.brandId(brandId)
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("화이트 바지");
	}

	@Test
	@DisplayName("성별 필터를 적용하면 해당 성별만 조회된다.")
	void searchProductsByGender() {
		// given
		productCommandService.createProduct(
			createProductRequest("남성 티셔츠", topsCategoryPath, ProductGenderType.MEN, BigDecimal.valueOf(10000)), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("여성 바지", bottomsCategoryPath, ProductGenderType.WOMEN, BigDecimal.valueOf(12000)), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.gender(ProductGenderType.MEN)
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("남성 티셔츠");
	}

	@Test
	@DisplayName("브랜드 필터를 적용하면 해당 브랜드 상품만 조회된다.")
	void searchProductsByBrand() {
		// given
		Brand otherBrand = saveBrandWithMember(2L);
		Long otherBrandId = otherBrand.getBrandId();

		productCommandService.createProduct(
			createProductRequest("우리 브랜드 티셔츠", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("다른 브랜드 바지", bottomsCategoryPath), otherBrandId, 2L);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.brandId(otherBrandId)
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("다른 브랜드 바지");
	}

    @Test
	@DisplayName("키워드가 있을 때 필터와 함께 검색된다.")
	void searchProductsWithKeyword() {
		// given
		Long blackTeeId = productCommandService.createProduct(
			createProductRequest("블랙 티셔츠", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("화이트 바지", bottomsCategoryPath), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.keyword("블랙")
			.categoryPaths(List.of(topsCategoryPath))
			.gender(ProductGenderType.ALL)
			.brandId(brandId)
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductId)
			.containsExactly(blackTeeId);
	}

	@Test
	@DisplayName("키워드가 공백이면 필터-only 경로를 탄다.")
	void searchProductsWithBlankKeywordTreatsAsNoKeyword() {
		// given
		productCommandService.createProduct(
			createProductRequest("블랙 티셔츠", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("화이트 바지", bottomsCategoryPath), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.keyword("   ")
			.categoryPaths(List.of(bottomsCategoryPath))
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("화이트 바지");
	}

	@Test
	@DisplayName("가격 낮은순 정렬이 적용된다.")
	void searchProductsSortedByLowestPrice() {
		// given
		productCommandService.createProduct(
			createProductRequest("저가 티셔츠", topsCategoryPath, ProductGenderType.ALL, BigDecimal.valueOf(5000)), brandId, userId);
		productCommandService.createProduct(
			createProductRequest("고가 티셔츠", topsCategoryPath, ProductGenderType.ALL, BigDecimal.valueOf(15000)), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.priceSort(ProductSearchCondition.PriceSort.LOWEST)
			.pageable(PageRequest.of(0, 10))
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("저가 티셔츠", "고가 티셔츠");
	}

    @Test
    @DisplayName("가격 높은순 정렬이 적용된다.")
    void searchProductsSortedByHighestPrice() {
        // given
        productCommandService.createProduct(
            createProductRequest("저가 티셔츠", topsCategoryPath, ProductGenderType.ALL, BigDecimal.valueOf(5000)), brandId, userId);
        productCommandService.createProduct(
            createProductRequest("고가 티셔츠", topsCategoryPath, ProductGenderType.ALL, BigDecimal.valueOf(15000)), brandId, userId);

        ProductSearchCondition condition = ProductSearchCondition.builder()
            .priceSort(ProductSearchCondition.PriceSort.HIGHEST)
            .pageable(PageRequest.of(0, 10))
            .build();

        // when
        ProductSearchResponse response = sut.searchProducts(condition);

        // then
        assertThat(response.getProducts())
            .extracting(ProductSearchResponse.ProductSummary::getProductName)
            .containsExactly("고가 티셔츠", "저가 티셔츠");
    }

	@Test
	@DisplayName("페이징이 적용된다.")
	void searchProductsWithPagination() {
		// given
		productCommandService.createProduct(createProductRequest("상품1", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(createProductRequest("상품2", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(createProductRequest("상품3", topsCategoryPath), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.pageable(PageRequest.of(1, 2)) // second page (0-based)
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getPage()).isEqualTo(1);
		assertThat(response.getSize()).isEqualTo(2);
		assertThat(response.getTotalElements()).isEqualTo(3);
		assertThat(response.getProducts())
			.extracting(ProductSearchResponse.ProductSummary::getProductName)
			.containsExactly("상품3");
	}

	@Test
	@DisplayName("unpaged 요청도 기본 페이지로 처리된다.")
	void searchProductsWithUnpagedConditionUsesDefaultPaging() {
		// given
		productCommandService.createProduct(createProductRequest("상품1", topsCategoryPath), brandId, userId);
		productCommandService.createProduct(createProductRequest("상품2", topsCategoryPath), brandId, userId);

		ProductSearchCondition condition = ProductSearchCondition.builder()
			.pageable(org.springframework.data.domain.Pageable.unpaged())
			.build();

		// when
		ProductSearchResponse response = sut.searchProducts(condition);

		// then
		assertThat(response.getPage()).isEqualTo(0);
		assertThat(response.getSize()).isEqualTo(24); // ensurePaged 기본값
		assertThat(response.getTotalElements()).isEqualTo(2);
	}

    @Test
    @DisplayName("단일 상품 상세 조회가 정상 동작한다.")
    void getProductDetail() {
        // given
        Long productId = productCommandService.createProduct(
            createProductRequest("상세 조회용 티셔츠", topsCategoryPath), brandId, userId); 
        
        // when
        var response = sut.getProductDetail(productId);
        
        // then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getProductName()).isEqualTo("상세 조회용 티셔츠");
    } 
    
    @Test
    @DisplayName("존재하지 않는 상품 상세 조회 시 예외가 발생한다.")
    void getProductDetail_NonExistentProduct_ThrowsException() {
        // given
        Long nonExistentProductId = 9999L;

        // when // then
        org.junit.jupiter.api.Assertions.assertThrows(
            com.mudosa.musinsa.exception.BusinessException.class,
            () -> sut.getProductDetail(nonExistentProductId)
        );
    }

    @Test
    @DisplayName("카테고리 트리를 부모-자식 구조로 반환한다.")
    void getCategoryTree_ReturnsTreeStructure() {
        // given: setUp에서 상의>티셔츠, 하의>바지 저장

        // when
        CategoryTreeResponse response = sut.getCategoryTree();

        // then
        assertThat(response.getCategories()).hasSize(2);

        CategoryTreeResponse.CategoryNode tops = findByName(response, "상의");
        CategoryTreeResponse.CategoryNode bottoms = findByName(response, "하의");

        assertThat(tops.getChildren())
            .extracting(CategoryTreeResponse.CategoryNode::getCategoryName)
            .containsExactly("티셔츠");
        assertThat(bottoms.getChildren())
            .extracting(CategoryTreeResponse.CategoryNode::getCategoryName)
            .containsExactly("바지");
    }

    // ==== helper methods ==== //
	private CategoryTreeResponse.CategoryNode findByName(CategoryTreeResponse response, String name) {
		return response.getCategories().stream()
			.filter(node -> node.getCategoryName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private ProductCreateRequest createProductRequest(String name, String categoryPath) {
		return createProductRequest(name, categoryPath, ProductGenderType.ALL, BigDecimal.valueOf(10000));
	}

	private ProductCreateRequest createProductRequest(String name, String categoryPath, ProductGenderType genderType, BigDecimal price) {
		return ProductCreateRequest.builder()
			.productName(name)
			.productInfo("상품 정보")
			.productGenderType(genderType)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(price)
				.stockQuantity(5)
				.optionValueIds(optionValueIds)
				.build()))
			.build();
	}

	private Brand saveBrandWithMember(Long userId) {
		Brand brand = Brand.create("브랜드", "BRAND", BigDecimal.ZERO);
		brand.addMember(BrandMember.create(userId));
		return brandRepository.save(brand);
	}

	private Category saveCategoryPath(String parentName, String childName) {
		Category parent = categoryRepository.save(Category.create(parentName, null, null));
		return categoryRepository.save(Category.create(childName, parent, null));
	}

	private OptionValue saveOptionValue(String optionName, String optionValue) {
		return optionValueRepository.save(OptionValue.create(optionName, optionValue));
	}
}
