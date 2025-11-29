package com.mudosa.musinsa.product.domain.model;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 도메인 모델의 테스트")
class ProductTest {

	private Brand brand;
	private Image image;
	private Inventory inventory;
	private ProductOption option;

	@BeforeEach
	void setUp() {
		brand = Brand.builder()
			.nameKo("테스트 브랜드")
			.nameEn("TEST")
			.status(BrandStatus.ACTIVE)
			.commissionRate(BigDecimal.ZERO)
			.build();

		image = Image.builder()
			.imageUrl("http://example.com/image.jpg")
			.isThumbnail(false)
			.build();

		inventory = Inventory.builder()
			.stockQuantity(new StockQuantity(10))
			.build();

		option = ProductOption.builder()
			.productPrice(new Money(10000L))
			.inventory(inventory)
			.build();
			
	}

	@Test
	@DisplayName("필수 값들을 넣고 상품을 생성하면 정상적으로 생성된다.")
	void createProduct() {
		// given
		List<Image> images = List.of(image);
		List<ProductOption> options = List.of(option);
		String categoryPath = "상의>티셔츠";

		// when
		Product product = Product.create(
			brand,
			"상품명",
			"상품 정보",
			ProductGenderType.ALL,
			brand.getNameKo(),
			categoryPath,
			true,
			BigDecimal.ZERO,
			images,
			options
		);

		// then
		assertThat(product.getProductName()).isEqualTo("상품명");
		assertThat(product.getProductInfo()).isEqualTo("상품 정보");
		assertThat(product.getProductGenderType()).isEqualTo(ProductGenderType.ALL);
	}

	@ParameterizedTest
	@MethodSource("requiredFieldsNull")
	@DisplayName("상품의 필수 정보가 비어 있으면 BusinessException이 발생한다.")
	void createProductWithNullOrBlank(String productName,
									  String productInfo,
									  ProductGenderType genderType,
									  ErrorCode expectedCode) {
		// given
		List<Image> images = List.of(image);
		List<ProductOption> options = List.of(option);
		String categoryPath = "상의>티셔츠";

		// when // then
		assertThatThrownBy(() -> Product.create(
			brand,
			productName,
			productInfo,
			genderType,
			brand.getNameKo(),
			categoryPath,
			true,
			BigDecimal.ZERO,
			images,
			options
		))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(expectedCode);
	}

	private static Stream<Arguments> requiredFieldsNull() {
		return Stream.of(
			Arguments.of(null, "정보", ProductGenderType.ALL, ErrorCode.PRODUCT_NAME_REQUIRED),
			Arguments.of("이름", null, ProductGenderType.ALL, ErrorCode.PRODUCT_INFO_REQUIRED),
			Arguments.of("이름", "정보", null, ErrorCode.PRODUCT_GENDER_TYPE_REQUIRED)
		);
	}

	@ParameterizedTest
	@MethodSource("requiredFieldsEmpty")
	@DisplayName("상품의 필수 정보에 빈 문자열을 넣으면 BusinessException이 발생한다.")
	void createProductWithEmptyValue(String productName,
									 String productInfo,
									 ErrorCode expectedCode) {
		// given
		List<Image> images = List.of(image);
		List<ProductOption> options = List.of(option);
		String categoryPath = "상의>티셔츠";

		// when // then
		assertThatThrownBy(() -> Product.create(
			brand,
			productName,
			productInfo,
			ProductGenderType.ALL,
			brand.getNameKo(),
			categoryPath,
			true,
			BigDecimal.ZERO,
			images,
			options
		))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(expectedCode);
	}

	private static Stream<Arguments> requiredFieldsEmpty() {
		return Stream.of(
			Arguments.of("", "정보", ErrorCode.PRODUCT_NAME_REQUIRED),
			Arguments.of(" ", "정보", ErrorCode.PRODUCT_NAME_REQUIRED),
			Arguments.of("이름", "", ErrorCode.PRODUCT_INFO_REQUIRED),
			Arguments.of("이름", " ", ErrorCode.PRODUCT_INFO_REQUIRED)
		);
	}

	@Test
	@DisplayName("상품에 이미지를 추가하면 연관관계가 설정된다.")
	void addImage() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.build();

		// when
		product.addImage(image);

		// then
		assertThat(product.getImages()).contains(image);
	}

	@Test
	@DisplayName("상품에 상품 옵션을 추가하면 연관관계가 설정된다.")
	void addProductOption() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.build();

		// when
		product.addProductOption(option);

		// then
		assertThat(product.getProductOptions()).contains(option);
	}

	@Test
	@DisplayName("대표 가격보다 낮은 옵션 가격이 들어오면 defaultPrice가 갱신된다.")
	void applyLowerDefaultPrice_updatesWhenLower() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.defaultPrice(BigDecimal.valueOf(10000))
			.build();

		// when
		product.applyLowerDefaultPrice(BigDecimal.valueOf(8000));

		// then
		assertThat(product.getDefaultPrice()).isEqualByComparingTo(BigDecimal.valueOf(8000));
	}

	@Test
	@DisplayName("대표 가격보다 높은 옵션 가격이 들어오면 defaultPrice는 유지된다.")
	void applyLowerDefaultPrice_ignoresWhenHigher() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.defaultPrice(BigDecimal.valueOf(10000))
			.build();

		// when
		product.applyLowerDefaultPrice(BigDecimal.valueOf(12000));

		// then
		assertThat(product.getDefaultPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
	}

	@ParameterizedTest
	@CsvSource({"false, false", "true, true"})
	@DisplayName("상품의 판매 가능 여부를 변경할 수 있다.")
	void changeAvailability(boolean isAvailable, boolean expectedAvailability) {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.isAvailable(isAvailable)
			.build();

		// when
		product.changeAvailability(expectedAvailability);

		// then
		assertThat(product.getIsAvailable()).isEqualTo(expectedAvailability);
	}

	@Test
	@DisplayName("상품의 수정가능한 정보의 값을 수정한다.")
	void updateProduct() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.build();

		// when
		product.updateBasicInfo("변경된 상품명", "변경된 상품 정보");

		// then
		assertThat(product.getProductName()).isEqualTo("변경된 상품명");
		assertThat(product.getProductInfo()).isEqualTo("변경된 상품 정보");
	}

	@Test
	@DisplayName("상품의 수정가능한 정보에 null 값을 넣고 수정하면 false를 반환한다.")
	void updateProductWithNullValue() {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.build();

		// when 
		boolean changed = product.updateBasicInfo(null, null);

		// then
		assertThat(changed).isFalse();
		assertThat(product.getProductName()).isEqualTo("상품명");
		assertThat(product.getProductInfo()).isEqualTo("상품 정보");

	}

	@ParameterizedTest
	@MethodSource("updateValuesEmpty")
	@DisplayName("상품의 수정가능한 정보에 빈값을 넣고 수정하면 BusinessException이 발생한다.")
	void updateProductWithEmptyValue(String productName,
									 String productInfo,
									 ErrorCode expectedCode) {
		// given
		Product product = Product.builder()
			.productName("상품명")
			.productInfo("상품 정보")
			.productGenderType(ProductGenderType.ALL)
			.brandName(brand.getNameKo())
			.categoryPath("상의>티셔츠")
			.build();

		// when // then
		assertThatThrownBy(() -> product.updateBasicInfo(productName, productInfo))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(expectedCode);
	}

	private static Stream<Arguments> updateValuesEmpty() {
		return Stream.of(
			Arguments.of(" ", "상품 정보", ErrorCode.PRODUCT_NAME_REQUIRED),
			Arguments.of("상품명", " ", ErrorCode.PRODUCT_INFO_REQUIRED)
		);
	}

}
