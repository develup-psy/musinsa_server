package com.mudosa.musinsa.product.domain.model;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductOption 도메인 모델의 테스트")
class ProductOptionTest {
    
    @Test
    @DisplayName("상품 옵션의 필수 정보를 넣으면 상품 옵션이 정상적으로 생성된다.")
    void createProductOption() {
        // given
        Product product = Product.builder().build();
        Inventory inventory = Inventory.create(new StockQuantity(100));

        // when
        ProductOption productOption = ProductOption.create(
            product,
            new Money(5000L),
            inventory
        );

        // then
        assertThat(productOption).isNotNull();
        assertThat(productOption.getProductPrice()).isEqualTo(new Money(5000L));
    }

    @ParameterizedTest
    @DisplayName("상품 옵션의 가격이 null 이거나 0이면 BusinessException이 발생한다.")
    @MethodSource("priceFieldsInvalid")
    void createProductOptionWithInvalidPrice(Money invalidPrice) {
        // given
        Product product = Product.builder().build();
        Inventory inventory = Inventory.create(new StockQuantity(10));

        // when // then
        assertThatThrownBy(() -> ProductOption.create(product, invalidPrice, inventory))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_PRICE_REQUIRED);
    }

    private static Stream<Arguments> priceFieldsInvalid() {
        return Stream.of(
            Arguments.of((Money) null),
            Arguments.of(new Money(0L))
        );
    }

    @Test
    @DisplayName("상품 옵션의 가격이 음수이면 IllegalArgumentException이 발생한다.")
    void createProductOptionWithNegativePrice() {
        // given
        Product product = Product.builder().build();
        Inventory inventory = Inventory.create(new StockQuantity(10));

        // when // then
        assertThatThrownBy(() -> ProductOption.create(product, new Money(-1L), inventory))
            .isInstanceOf(IllegalArgumentException.class)
            .extracting(e -> ((IllegalArgumentException) e).getMessage())
            .isEqualTo("금액은 음수일 수 없습니다.");
    }

    @Test
    @DisplayName("상품 옵션의 인벤토리가 null이면 BusinessException이 발생한다.")
    void createProductOptionWithNullInventory() {
        // given
        Product product = Product.builder().build();

        // when // then
        assertThatThrownBy(() -> ProductOption.create(product, new Money(5000L), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVENTORY_REQUIRED);
    }

    @Test
    @DisplayName("상품 옵션에 옵션 값을 추가하면 연관관계가 설정된다.")
    void addOptionValue() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        ProductOptionValue optionValue = ProductOptionValue.builder()
            .optionValue(OptionValue.builder().build())
            .build();
        
        // when
        productOption.addOptionValue(optionValue);

        // then
        assertThat(productOption.getProductOptionValues()).contains(optionValue);
    }

    @Test
    @DisplayName("상품 옵션과 옵션 값을 연결할때 옵션 값이 null이면 BusinessException이 발생한다.")
    void addOptionValueWithNullOptionValue() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        // when // then
        assertThatThrownBy(() -> productOption.addOptionValue(null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.OPTION_VALUE_REQUIRED);
    }

    @Test
    @DisplayName("상품 옵션에 상품을 연결하면 연관관계가 설정된다.")
    void setProduct() {
        // given
        Product product = Product.builder().build();
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        // when
        productOption.setProduct(product);

        // then
        assertThat(productOption.getProduct()).isEqualTo(product);
    }
    
    @Test
    @DisplayName("상품 옵션의 재고를 정상적으로 차감한다.")
    void decreaseStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        // when
        productOption.decreaseStock(10);

        // then
        assertThat(productOption.getStockQuantity()).isEqualTo(40);
    }

    @ParameterizedTest
    @DisplayName("상품 옵션의 재고를 차감할 때 차감 수량이 0 이하이면 BusinessException이 발생한다.")
    @MethodSource("decreaseStockInvalidQuantities")
    void decreaseStockWithInvalidQuantity(int decreaseStock) {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        // when // then
        assertThatThrownBy(() -> productOption.decreaseStock(decreaseStock))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
    }

    private static Stream<Arguments> decreaseStockInvalidQuantities() {
        return Stream.of(
            Arguments.of(0),
            Arguments.of(-1)
        );
    }

    @Test
    @DisplayName("상품 옵션의 재고를 차감할 때 재고보다 큰 값을 요청하면 BusinessException이 발생한다.")
    void decreaseStockWithInsufficientStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(5)))
            .build();

        // when // then
        assertThatThrownBy(() -> productOption.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("상품 옵션의 재고를 정상적으로 복구(추가)한다.")
    void restoreStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();

        // when
        productOption.restoreStock(20);

        // then
        assertThat(productOption.getStockQuantity()).isEqualTo(70);
    }

    @ParameterizedTest
    @DisplayName("상품 옵션의 재고를 복구할 때 복구 수량이 0 이하이면 BusinessException이 발생한다.")
    @MethodSource("restoreStockInvalidQuantities")
    void restoreStockWithInvalidQuantity(int restoreStock) {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(50)))
            .build();
        
        // when // then
        assertThatThrownBy(() -> productOption.restoreStock(restoreStock))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.RECOVER_VALUE_INVALID);
    }

    private static Stream<Arguments> restoreStockInvalidQuantities() {
        return Stream.of(
            Arguments.of(0),
            Arguments.of(-5)
        );
    }

    @Test
    @DisplayName("상품 옵션의 판매 가능 여부를 조회한다.")
    void validateAvailable() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(10)))
            .build();

        // when
        productOption.validateAvailable();

        // then - 예외가 발생하지 않음
    }

    @Test
    @DisplayName("상품 옵션의 재고가 없으면 판매 불가능 상태로 BusinessException이 발생한다.")
    void isAvailableWithOutOfStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(0)))
            .build();

        // when // then
        assertThatThrownBy(() -> productOption.validateAvailable())
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_OPTION_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("상품 옵션의 재고 수량을 조회한다.")
    void getStockQuantity() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(25)))
            .build();
        
        // when
        productOption.getStockQuantity();
        
        // then
        assertThat(productOption.getStockQuantity()).isEqualTo(25);
    }

    @Test
    @DisplayName("상품 옵션의 재고가 요청 수량보다 충분한지 확인한다. case: 충분함")
    void hasEnoughStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(30)))
            .build();

        // when
        boolean hasStock = productOption.hasEnoughStock(20);

        // then
        assertThat(hasStock).isTrue();
    }

    @Test
    @DisplayName("상품 옵션의 재고가 요청 수량보다 충분한지 확인한다. case: 부족함")
    void hasEnoughStockWithNotEnoughStock() {
        // given
        ProductOption productOption = ProductOption.builder()
            .productPrice(new Money(5000L))
            .inventory(Inventory.create(new StockQuantity(30)))
            .build();

        // when
        boolean hasStock = productOption.hasEnoughStock(50);

        // then
        assertThat(hasStock).isFalse();
    }

	@Test
	@DisplayName("동일한 조합일떄 equals/hashCode가 동일하다.")
	void equalsAndHashCodeSameValues() {
		// given
		ProductOptionValue.ProductOptionValueId id1 = new ProductOptionValue.ProductOptionValueId(1L, 2L);
		ProductOptionValue.ProductOptionValueId id2 = new ProductOptionValue.ProductOptionValueId(1L, 2L);

		// when // then
		assertThat(id1).isEqualTo(id2);
		assertThat(id1).hasSameHashCodeAs(id2);
	}

	@Test
	@DisplayName("옵션 아이디나 옵션 값 아이디가 다르면 equals가 false다.")
	void equalsReturnsFalseForDifferentValues() {
		// given
		ProductOptionValue.ProductOptionValueId id = new ProductOptionValue.ProductOptionValueId(1L, 2L);
		ProductOptionValue.ProductOptionValueId differentProduct = new ProductOptionValue.ProductOptionValueId(99L, 2L);
		ProductOptionValue.ProductOptionValueId differentOption = new ProductOptionValue.ProductOptionValueId(1L, 99L);

		// when // then
		assertThat(id).isNotEqualTo(differentProduct);
		assertThat(id).isNotEqualTo(differentOption);
	}

    
}  