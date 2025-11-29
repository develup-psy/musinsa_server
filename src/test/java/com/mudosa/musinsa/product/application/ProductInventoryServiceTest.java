package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandMember;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.ProductCreateRequest;
import com.mudosa.musinsa.product.application.dto.StockAdjustmentRequest;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.InventoryRepository;
import com.mudosa.musinsa.product.domain.repository.OptionValueRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductInventoryService 테스트")
@Transactional
class ProductInventoryServiceTest extends ServiceConfig {

	@Autowired
	private ProductInventoryService sut;
	@Autowired
	private ProductCommandService productCommandService;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductOptionRepository productOptionRepository;
	@Autowired
	private InventoryRepository inventoryRepository;
	@Autowired
	private BrandRepository brandRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private OptionValueRepository optionValueRepository;

	private Long userId;
	private Long brandId;
	private String categoryPath;
	private Long sizeLId;
	private Long sizeMId;
	private Long colorBlackId;

	@BeforeEach
	void setUp() {
		userId = 1L;
		brandId = saveBrandWithMember(userId).getBrandId();
		categoryPath = saveCategoryPath("상의", "티셔츠").buildPath();
		sizeLId = saveOptionValue("사이즈", "L").getOptionValueId();
		sizeMId = saveOptionValue("사이즈", "M").getOptionValueId();
		colorBlackId = saveOptionValue("색상", "블랙").getOptionValueId();
	}

	@Test
	@DisplayName("브랜드 관리자는 상품 옵션 재고 현황을 조회할 수 있다.")
	void getProductOptionStocks() {
		// given
		Long productId = createProduct(
			"다중 옵션 티셔츠",
			List.of(
				optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)),
				optionRequest(BigDecimal.valueOf(11000), 0, List.of(sizeMId, colorBlackId))
			)
		);
		List<ProductOption> productOptions = findProductOptions(productId);
		ProductOption inStockOption = productOptions.stream()
			.filter(po -> po.getInventory().getStockQuantity().getValue() == 5)
			.findFirst()
			.orElseThrow();
		ProductOption outOfStockOption = productOptions.stream()
			.filter(po -> po.getInventory().getStockQuantity().getValue() == 0)
			.findFirst()
			.orElseThrow();

		// when
		var responses = sut.getProductOptionStocks(brandId, productId, userId);

		// then
		assertThat(responses).hasSize(2);
		var inStockResponse = responses.stream()
			.filter(res -> res.getProductOptionId().equals(inStockOption.getProductOptionId()))
			.findFirst()
			.orElseThrow();
		var outOfStockResponse = responses.stream()
			.filter(res -> res.getProductOptionId().equals(outOfStockOption.getProductOptionId()))
			.findFirst()
			.orElseThrow();

		assertThat(inStockResponse.getStockQuantity()).isEqualTo(5);
		assertThat(inStockResponse.getHasStock()).isTrue();

		assertThat(outOfStockResponse.getStockQuantity()).isZero();
		assertThat(outOfStockResponse.getHasStock()).isFalse();
	}

	@Test
	@DisplayName("브랜드 멤버가 아니면 재고 조회가 거부된다.")
	void getProductOptionStocksWithNonMemberThrows() {
		// given
		Long productId = createProduct(
			"멤버 아님 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long nonMemberUserId = 9999L;

		// when // then
		assertThatThrownBy(() -> sut.getProductOptionStocks(brandId, productId, nonMemberUserId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOT_BRAND_MEMBER);
	}

	@Test
	@DisplayName("재고를 추가하면 수량이 증가한다.")
	void addStockIncreasesQuantity() {
		// given
		Long productId = createProduct(
			"재고 추가 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long productOptionId = findProductOptions(productId).get(0).getProductOptionId();
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(productOptionId)
			.quantity(3)
			.build();

		// when
		var response = sut.addStock(brandId, productId, request, userId);

		// then
		assertThat(response.getStockQuantity()).isEqualTo(8);
		assertThat(response.getHasStock()).isTrue();
		assertThat(inventoryRepository.findByProductOptionId(productOptionId))
			.get()
			.extracting(inv -> inv.getStockQuantity().getValue())
			.isEqualTo(8);
	}

	@Test
	@DisplayName("다른 상품의 옵션으로 재고를 조정하면 BusinessException이 발생한다.")
	void adjustStockWithOptionNotBelongingToProductThrows() {
		// given
		Long productId = createProduct(
			"상품 A",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long otherProductId = createProduct(
			"상품 B",
			List.of(optionRequest(BigDecimal.valueOf(12000), 3, List.of(sizeMId, colorBlackId)))
		);
		Long otherProductOptionId = findProductOptions(otherProductId).get(0).getProductOptionId();

		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(otherProductOptionId)
			.quantity(1)
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addStock(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_FOUND);

		assertThatThrownBy(() -> sut.subtractStock(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
	}

	@Test
	@DisplayName("다른 브랜드 상품의 재고를 조정하려 하면 예외가 발생한다.")
	void adjustStockForOtherBrandThrows() {
		// given
		Long productId = createProduct(
			"브랜드 불일치 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long productOptionId = findProductOptions(productId).get(0).getProductOptionId();
		Brand otherBrand = saveBrandWithMember(userId);
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(productOptionId)
			.quantity(1)
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addStock(otherBrand.getBrandId(), productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
	}

	@Test
	@DisplayName("재고를 차감하면 수량이 감소한다.")
	void subtractStockDecreasesQuantity() {
		// given
		Long productId = createProduct(
			"재고 차감 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long productOptionId = findProductOptions(productId).get(0).getProductOptionId();
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(productOptionId)
			.quantity(2)
			.build();

		// when
		var response = sut.subtractStock(brandId, productId, request, userId);

		// then
		assertThat(response.getStockQuantity()).isEqualTo(3);
		assertThat(response.getHasStock()).isTrue();
	}

	@Test
	@DisplayName("재고보다 많이 차감하면 BusinessException이 발생한다.")
	void subtractStockWithInsufficientQuantityThrows() {
		// given
		Long productId = createProduct(
			"재고 부족 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 1, List.of(sizeLId, colorBlackId)))
		);
		Long productOptionId = findProductOptions(productId).get(0).getProductOptionId();
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(productOptionId)
			.quantity(5)
			.build();

		// when // then
		assertThatThrownBy(() -> sut.subtractStock(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
	}

	@ParameterizedTest
	@MethodSource("invalidQuantityProvider")
	@DisplayName("유효하지 않은 수량으로 재고를 조정하려 하면 예외가 발생한다.")
	void adjustStockWithInvalidQuantityThrows(Integer invalidQuantity, ErrorCode expectedErrorCode) {
		// given
		Long productId = createProduct(
			"유효하지 않은 수량 티셔츠",
			List.of(optionRequest(BigDecimal.valueOf(10000), 5, List.of(sizeLId, colorBlackId)))
		);
		Long productOptionId = findProductOptions(productId).get(0).getProductOptionId();
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(productOptionId)
			.quantity(invalidQuantity)
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addStock(brandId, productId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(expectedErrorCode);
	}

	static Stream<Arguments> invalidQuantityProvider() {
		return Stream.of(
			Arguments.of(null, ErrorCode.INVALID_INVENTORY_UPDATE_VALUE),
			Arguments.of(0, ErrorCode.INVALID_INVENTORY_UPDATE_VALUE),
			Arguments.of(-1, ErrorCode.INVALID_INVENTORY_UPDATE_VALUE)
		);
	}

	@Test
	@DisplayName("상품이 null 일때 브랜드의 상품의 옵션을 로드하려 하면 예외가 발생한다.")
	void loadProductOptionForBrandWithNullProductThrows() {
		// given
		Long nonExistentProductId = 9999L;
		StockAdjustmentRequest request = StockAdjustmentRequest.builder()
			.productOptionId(1L)
			.quantity(1)
			.build();

		// when // then
		assertThatThrownBy(() -> sut.addStock(brandId, nonExistentProductId, request, userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
	}

	// ==== helper methods ==== //
	private Long createProduct(String name, List<ProductCreateRequest.OptionCreateRequest> options) {
		ProductCreateRequest request = ProductCreateRequest.builder()
			.productName(name)
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.categoryPath(categoryPath)
			.isAvailable(true)
			.images(List.of(ProductCreateRequest.ImageCreateRequest.builder()
				.imageUrl("http://example.com/thumb.jpg")
				.isThumbnail(true)
				.build()))
			.options(options)
			.build();

		return productCommandService.createProduct(request, brandId, userId);
	}

	private ProductCreateRequest.OptionCreateRequest optionRequest(BigDecimal price, int stock, List<Long> optionValueIds) {
		return ProductCreateRequest.OptionCreateRequest.builder()
			.productPrice(price)
			.stockQuantity(stock)
			.optionValueIds(optionValueIds)
			.build();
	}

	private List<ProductOption> findProductOptions(Long productId) {
		Product product = productRepository.findById(productId).orElseThrow();
		return productOptionRepository.findAllByProduct(product);
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
