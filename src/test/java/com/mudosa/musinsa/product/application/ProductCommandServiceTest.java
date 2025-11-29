package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandMember;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.product.application.dto.ProductCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductUpdateRequest;
import com.mudosa.musinsa.product.application.dto.ProductOptionCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductManagerResponse;
import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.OptionValueRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductCommandService 테스트")
@Transactional
class ProductCommandServiceTest extends ServiceConfig {

	@Autowired
	private ProductCommandService sut;

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private BrandRepository brandRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private OptionValueRepository optionValueRepository;

	private Long userId;
	private Long brandId;
	private List<Long> optionValueIds;
	private String categoryPath;

	@BeforeEach
	void setUp() {
		userId = 1L;
		brandId = saveBrandWithMember(userId).getBrandId();
		categoryPath = saveCategoryPath("상의", "티셔츠").buildPath();
		optionValueIds = List.of(
			saveOptionValue("사이즈", "L").getOptionValueId(),
			saveOptionValue("색상", "블랙").getOptionValueId()
		);
	}

	@Test
	@DisplayName("올바른 요청으로 상품 생성시 상품이 정상적으로 생성되고 id를 반환한다.")
	void createProduct() {
		// given
		ProductCreateRequest request = createProductRequest();

		// when
		Long productId = sut.createProduct(request, brandId, userId);

		// then
		assertThat(productId).isNotNull();

		Product saved = productRepository.findById(productId).orElseThrow();
		assertThat(saved.getProductId()).isEqualTo(productId);
		assertThat(saved.getProductName()).isEqualTo("상품명");
		assertThat(saved.getImages()).hasSize(1);
		assertThat(saved.getProductOptions()).hasSize(1);
	}

	@Test
	@DisplayName("재고 수량이 0인 옵션으로도 상품이 생성된다.")
	void createProductWithZeroStock() {
		// given
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(10000))
				.stockQuantity(0)
				.optionValueIds(optionValueIds)
				.build()))
			.build();

		// when
		Long productId = sut.createProduct(request, brandId, userId);

		// then
		Product saved = productRepository.findById(productId).orElseThrow();
		assertThat(saved.getProductOptions().get(0).getInventory().getStockQuantity().getValue()).isZero();
	}

	@Test
	@DisplayName("재고 수량이 음수이면 상품 생성 시 BusinessException이 발생한다.")
	void createProductWithNegativeStock() {
		// given
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(10000))
				.stockQuantity(-1)
				.optionValueIds(optionValueIds)
				.build()))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.STOCK_QUANTITY_CANNOT_BE_NEGATIVE);
	}

	@Test
	@DisplayName("존재하지 않는 브랜드로 상품 생성 시 BusinessException이 발생한다.")
	void createProductWithBrandNotExist() {
		// given
		ProductCreateRequest request = createProductRequest();

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, 99999L, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.BRAND_NOT_MATCHED);
	}

	@Test
	@DisplayName("브랜드 멤버가 아닌 사용자가 상품 생성 시 BusinessException이 발생한다.")
	void createProductWithNotBrandMember() {
		// given
		ProductCreateRequest request = createProductRequest();
		Long anotherUserId = 999L;

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, anotherUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("존재하지 않는 카테고리 경로로 상품 생성 시 BusinessException이 발생한다.")
	void createProductWithInvalidCategoryPath() {
		// given
		ProductCreateRequest request = createProductRequest("없는/경로");

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_CATEGORY_REQUIRED);
	}

	@Test
	@DisplayName("존재하지 않는 상품 옵션 값을 사용하여 상품 생성 시 BusinessException이 발생한다.")
	void createProductOptionWithNotExistOptionValue() {
		// given
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(10000))
				.stockQuantity(5)
				.optionValueIds(List.of(99999L, 88888L)) // 존재하지 않는 옵션 값 ID
				.build()))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_PRODUCT_OPTION_VALUE_IDS);
	}

	@Test
	@DisplayName("이미 존재하는 옵션의 조합으로 상품 옵션을 생성할 시 BusinessException이 발생한다.")
	void createProductWithDuplicateOptionCombination() {
		// given
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(
				ProductCreateRequest.OptionCreateRequest.builder()
					.productPrice(BigDecimal.valueOf(10000))
					.stockQuantity(5)
					.optionValueIds(optionValueIds)
					.build(),
				ProductCreateRequest.OptionCreateRequest.builder()
					.productPrice(BigDecimal.valueOf(12000))
					.stockQuantity(3)
					.optionValueIds(optionValueIds) // 중복된 조합
					.build()
			))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.DUPLICATE_PRODUCT_OPTION_COMBINATION);
	}

	@Test
	@DisplayName("올바른 요청으로 상품 수정시 상품이 정상적으로 수정된다.")
	void updateProduct() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductUpdateRequest updateRequest = createProductUpdateRequest();

		// when
		var updated = sut.updateProduct(brandId, productId, updateRequest, userId);

		// then
		assertThat(updated.getProductName()).isEqualTo("수정된 상품명");
		assertThat(updated.getProductInfo()).isEqualTo("수정된 상품 정보");
		assertThat(updated.getIsAvailable()).isFalse();
		assertThat(updated.getImages()).hasSize(1);
		assertThat(updated.getImages().iterator().next().getImageUrl()).isEqualTo("http://example.com/updated_thumb.jpg");
	}

	@Test
	@DisplayName("브랜드 멤버가 아닌 사용자가 상품 수정 시 BusinessException이 발생한다.")
	void updateProductWithNotBrandMember() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductUpdateRequest updateRequest = createProductUpdateRequest();
		Long anotherUserId = 999L;

		// when // then
		assertThatThrownBy(() -> sut.updateProduct(brandId, productId, updateRequest, anotherUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("존재하지 않는 상품 수정 시 BusinessException이 발생한다.")
    void updateProductWithNotExistProduct() {
		// given
		ProductUpdateRequest updateRequest = createProductUpdateRequest();
		Long nonExistentProductId = 99999L;

		// when // then
		assertThatThrownBy(() -> sut.updateProduct(brandId, nonExistentProductId, updateRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
	}

	@Test
	@DisplayName("수정 할 값을 넣지않고 상품 수정 시 BusinessException이 발생한다.")
	void updateProductWithNoUpdatableField() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductUpdateRequest updateRequest = ProductUpdateRequest.builder().build();

		// when // then
		assertThatThrownBy(() -> sut.updateProduct(brandId, productId, updateRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NOTHING_TO_UPDATE);
	}

	@Test
	@DisplayName("상품 수정 시 이미지를 하나도 넣지 않으면 BusinessException이 발생한다.")
	void updateProductWithoutImages() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductUpdateRequest updateRequest = ProductUpdateRequest.builder()
			.productName("수정된 상품명")
			.productInfo("수정된 상품 정보")
			.isAvailable(false)
			.images(List.of()) // 이미지 없음
			.build();

		// when // then
		assertThatThrownBy(() -> sut.updateProduct(brandId, productId, updateRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.IMAGE_REQUIRED);
	}

	@Test
	@DisplayName("수정 요청에 변경된 값이 없으면 BusinessException이 발생한다.")
	void updateProductWithNoChanges() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductUpdateRequest updateRequest = ProductUpdateRequest.builder()
			.productName("상품명") // 기존과 동일
			.productInfo("상품 정보") // 기존과 동일
			.build();

		// when // then
		assertThatThrownBy(() -> sut.updateProduct(brandId, productId, updateRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NO_CHANGES_DETECTED);
	}

	@Test
	@DisplayName("올바른 요청으로 상품 옵션을 추가시 정상적으로 추가된다.")
	void addProductOption() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long newSizeId = saveOptionValue("사이즈", "XL").getOptionValueId();
		Long newColorId = saveOptionValue("색상", "화이트").getOptionValueId();

		ProductOptionCreateRequest newOptionRequest =
			ProductOptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(15000))
				.stockQuantity(10)
				.optionValueIds(List.of(newSizeId, newColorId))
				.build();

		// when
		sut.addProductOption(brandId, productId, newOptionRequest, userId);

		// then
		Product saved = productRepository.findById(productId).orElseThrow();
		assertThat(saved.getProductOptions()).hasSize(2);
		assertThat(saved.getProductOptions())
			.anyMatch(po -> po.getProductOptionValues().stream()
				.map(pov -> pov.getOptionValue().getOptionValue())
				.toList()
				.containsAll(List.of("XL", "화이트")));
	}

	@Test
	@DisplayName("브랜드 멤버가 아닌 사용자가 상품 옵션 추가 시 BusinessException이 발생한다.")
	void addProductOptionWithNotBrandMember() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductOptionCreateRequest newOptionRequest =
			ProductOptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(15000))
				.stockQuantity(10)
				.optionValueIds(optionValueIds)
				.build();
		Long anotherUserId = 999L;

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, newOptionRequest, anotherUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("존재하지 않는 상품에 옵션 추가 시 BusinessException이 발생한다.")
	void addProductOptionWithNotExistProduct() {
		// given
		Long nonExistentProductId = 99999L;
		ProductOptionCreateRequest newOptionRequest =
			ProductOptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(15000))
				.stockQuantity(10)
				.optionValueIds(optionValueIds)
				.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, nonExistentProductId, newOptionRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
	}

	@Test
	@DisplayName("존재하지 않는 상품 옵션 값을 사용하여 상품 옵션을 추가할 시 BusinessException이 발생한다.")
	void addProductOptionWithNotExistOptionValue() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductOptionCreateRequest newOptionRequest =
			ProductOptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(15000))
				.stockQuantity(10)
				.optionValueIds(List.of(99999L, 88888L)) // 존재하지 않는 옵션 값 ID
				.build();	

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, newOptionRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_PRODUCT_OPTION_VALUE_IDS);
	}

	@Test
	@DisplayName("이미 존재하는 옵션의 조합으로 상품 옵션을 추가할 시 BusinessException이 발생한다.")
	void addProductOptionWithDuplicateOptionCombination() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		ProductOptionCreateRequest newOptionRequest =
			ProductOptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(15000))
				.stockQuantity(10)
				.optionValueIds(optionValueIds) // 기존에 존재하는 조합
				.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, newOptionRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.DUPLICATE_PRODUCT_OPTION_COMBINATION);
	}

	@Test
	@DisplayName("옵션 값이 두 개가 아니면 BusinessException이 발생한다.")
	void addProductOptionWithMissingOptionValues() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long sizeId = saveOptionValue("사이즈", "XL").getOptionValueId(); // 색상 누락

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(sizeId))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("색상만 포함하고 사이즈가 없으면 BusinessException이 발생한다.")
	void addProductOptionWithMissingSizeOnly() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long colorId = optionValueIds.get(1); // 블랙

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(colorId))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("사이즈/색상 외 옵션 이름을 포함하면 BusinessException이 발생한다.")
	void addProductOptionWithInvalidOptionName() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long sizeId = saveOptionValue("사이즈", "XL").getOptionValueId();
		Long invalidId = saveOptionValue("소재", "면").getOptionValueId(); // 색상이 아님

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(sizeId, invalidId))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_PRODUCT_OPTION_VALUE);
	}

	@Test
	@DisplayName("옵션 값이 두 개가 다 null 이면 BusinessException이 발생한다.")
	void addProductOptionWithOptionValuesNull() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(null) // null
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("옵션 값 ID가 사이즈만 두 번이거나 색상만 두 번이면 BusinessException이 발생한다.")
	void addProductOptionWithDuplicateSizeOrColor() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long sizeId = optionValueIds.get(0); // L
		Long colorId = optionValueIds.get(1); // 블랙

		ProductOptionCreateRequest duplicateSizeRequest = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(sizeId, sizeId))
			.build();

		ProductOptionCreateRequest duplicateColorRequest = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(colorId, colorId))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, duplicateSizeRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);

		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, duplicateColorRequest, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("옵션 값 ID 목록이 비어있으면 BusinessException이 발생한다.")
	void addProductOptionWithEmptyOptionValues() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of()) // empty
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("옵션 값이 2개 초과이면 BusinessException이 발생한다.")
	void addProductOptionWithMoreThanTwoOptionValues() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long extraOptionId = saveOptionValue("소재", "면").getOptionValueId();

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(optionValueIds.get(0), optionValueIds.get(1), extraOptionId))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addProductOption(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);
	}

	@Test
	@DisplayName("옵션 이름에 공백이 포함되어도 정상 처리된다.")
	void addProductOptionWithWhitespaceOptionNames() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long spacedSizeId = saveOptionValue("  사이즈 ", "XL").getOptionValueId();
		Long spacedColorId = saveOptionValue(" 색상", "화이트").getOptionValueId();

		ProductOptionCreateRequest request = ProductOptionCreateRequest.builder()
			.productPrice(BigDecimal.valueOf(15000))
			.stockQuantity(5)
			.optionValueIds(List.of(spacedSizeId, spacedColorId))
			.build();

		// when
		var added = sut.addProductOption(brandId, productId, request, userId);

		// then
		assertThat(added.getOptionValues())
			.extracting(ProductDetailResponse.OptionDetail.OptionValueDetail::getOptionName)
			.containsExactlyInAnyOrder("사이즈", "색상");
	}

	@Test
	@DisplayName("올바른 요청으로 상품 목록 조회시 정상적으로 조회된다.")
	void getBrandProductsForManager() {
		// given
		ProductCreateRequest first = createProductRequest();
		ProductCreateRequest second = ProductCreateRequest.builder()
			.productName("상품명2")
			.productInfo("상품 정보2")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(false)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb2.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(20000))
				.stockQuantity(3)
				.optionValueIds(optionValueIds)
				.build()))
			.build();
		sut.createProduct(first, brandId, userId);
		sut.createProduct(second, brandId, userId);

		// when
		List<ProductManagerResponse> responses = sut.getBrandProductsForManager(brandId, userId);

		// then
		assertThat(responses).hasSize(2);
		assertThat(responses)
			.extracting(ProductManagerResponse::getProductName)
			.containsExactly("상품명", "상품명2");
	}

	@Test
	@DisplayName("브랜드 멤버가 아닌 사용자가 상품 목록 조회 시 BusinessException이 발생한다.")
	void getBrandProductsForManagerWithNotBrandMember() {
		// given
		Long anotherUserId = 999L;

		// when // then
		assertThatThrownBy(() -> sut.getBrandProductsForManager(brandId, anotherUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("올바른 요청으로 상품 상세 조회시 정상적으로 조회된다.")
	void getProductDetailForManager() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);

		// when
		ProductManagerResponse response = sut.getProductDetailForManager(brandId, productId, userId);

		// then
		assertThat(response.getProductId()).isEqualTo(productId);
		assertThat(response.getProductName()).isEqualTo("상품명");
		assertThat(response.getCategoryPath()).isEqualTo(categoryPath);
		assertThat(response.getProductGenderType()).isEqualTo(ProductGenderType.ALL);
		assertThat(response.getIsAvailable()).isTrue();
		assertThat(response.getImages()).hasSize(1);
		assertThat(response.getOptions()).hasSize(1);
		assertThat(response.getOptions().get(0).getPrice()).isEqualByComparingTo("10000");
		assertThat(response.getOptions().get(0).getOptionValues())
			.containsExactlyInAnyOrder("L", "블랙");
	}

	@Test
	@DisplayName("브랜드 멤버가 아닌 사용자가 상품 상세 조회 시 BusinessException이 발생한다.")
	void getProductDetailForManagerWithNotBrandMember() {
		// given
		ProductCreateRequest createRequest = createProductRequest();
		Long productId = sut.createProduct(createRequest, brandId, userId);
		Long anotherUserId = 999L;

		// when // then
		assertThatThrownBy(() -> sut.getProductDetailForManager(brandId, productId, anotherUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("옵션 조합 equals/hashCode가 동일한 조합을 동일하게 판단한다.")
	void optionCombinationEquality() throws Exception {
		Class<?> comboClass = Class.forName("com.mudosa.musinsa.product.application.ProductCommandService$OptionCombination");
		Constructor<?> ctor = comboClass.getDeclaredConstructor(Long.class, Long.class);
		ctor.setAccessible(true);

		Object combo1 = ctor.newInstance(1L, 2L);
		Object combo2 = ctor.newInstance(1L, 2L);
		Object combo3 = ctor.newInstance(1L, 3L);

		assertThat(combo1).isEqualTo(combo2);
		assertThat(combo1).isNotEqualTo(combo3);
		assertThat(combo1).hasSameHashCodeAs(combo2);
	}

	@Test
	@DisplayName("상품 생성 시 옵션 최저가가 default_price로 저장된다.")
	void createProductSetsDefaultPriceFromOptions() {
		// given
		Long spacedSizeId = saveOptionValue("  사이즈 ", "XL").getOptionValueId();
		Long spacedColorId = saveOptionValue(" 색상", "화이트").getOptionValueId();
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(
				ProductCreateRequest.OptionCreateRequest.builder()
					.productPrice(BigDecimal.valueOf(15000))
					.stockQuantity(5)
					.optionValueIds(optionValueIds)
					.build(),
				ProductCreateRequest.OptionCreateRequest.builder()
					.productPrice(BigDecimal.valueOf(8000))
					.stockQuantity(3)
					.optionValueIds(List.of(spacedSizeId, spacedColorId))
					.build()
			))
			.build();

		// when
		Long productId = sut.createProduct(request, brandId, userId);
		Product product = productRepository.findById(productId).orElseThrow();

		// then
		assertThat(product.getDefaultPrice()).isEqualByComparingTo("8000");
	}

	@Test
	@DisplayName("상품 생성 시 옵션 가격이 없으면 예외가 발생한다.")
	void createProductWithoutOptionPriceThrows() {
		// given
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(
				ProductCreateRequest.OptionCreateRequest.builder()
					.productPrice(null)
					.stockQuantity(5)
					.optionValueIds(optionValueIds)
					.build()
			))
			.build();

		// when // then
		assertThatThrownBy(() -> sut.createProduct(request, brandId, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_PRICE_REQUIRED);
	}


	// ==== helper methods ==== //
	private ProductCreateRequest createProductRequest() {
		return createProductRequest(categoryPath);
	}

	private ProductCreateRequest createProductRequest(String path) {
		return ProductCreateRequest.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(path)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(List.of(ProductCreateRequest.OptionCreateRequest.builder()
				.productPrice(BigDecimal.valueOf(10000))
				.stockQuantity(5)
				.optionValueIds(optionValueIds)
				.build()))
			.build();
	}

	private ProductUpdateRequest createProductUpdateRequest() {
		return ProductUpdateRequest.builder()
			.productName("수정된 상품명")
			.productInfo("수정된 상품 정보")
			.isAvailable(false)
			.images(List.of(ProductUpdateRequest.ImageUpdateRequest.builder()
				.imageUrl("http://example.com/updated_thumb.jpg")
				.isThumbnail(true)
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
