package com.mudosa.musinsa.order.domain.model;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.product.domain.model.Inventory;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @DisplayName("주문 생성시 주문 상태는 PENDING이다.")
    @Test
    void createOrderWithPending(){
        //given
        Brand brand = createBrand();
        Product product = createProduct(brand);
        User user = createUser();
        Inventory inventory = createInventory(10);
        ProductOption productOption = createProductOption(product, inventory, 10000L);
        Map<ProductOption, Integer> orderProductsWithQuantity = Map.of(productOption, 2);

        //when
        Order order = Order.create(1L, 1L, orderProductsWithQuantity, user);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("주문 상품 목록이 없이 주문을 생성할 때 예외를 발생한다. ")
    @Test
    void createOrderWithNoProductOptions(){
        //given
        Map<ProductOption, Integer> orderProductsWithQuantity = null;
        User user = createUser();

        //when & then
        assertThatThrownBy(() -> Order.create(1L, 1L, orderProductsWithQuantity,user))
                .isInstanceOf(BusinessException.class)
                .hasMessage("상품 목록이 없는 주문은 생성할 수 없습니다");
    }

    @DisplayName("주문 상품의 개수가 음수일 때 예외를 발생한다.")
    @Test
    void createOrderProductWithMinusQuantity(){
        //given
        Brand brand = createBrand();
        Product product = createProduct(brand);
        Inventory inventory = createInventory(10);
        ProductOption productOption = createProductOption(product, inventory, 10000L);
        Map<ProductOption, Integer> orderProductsWithQuantity = Map.of(productOption, -10);
        User user = createUser();

        //when & then
        assertThatThrownBy(() -> Order.create(1L, 1L, orderProductsWithQuantity,user))
                .isInstanceOf(BusinessException.class)
                .hasMessage("상품은 1개 이상 주문 가능합니다");
    }


    @DisplayName("주문 생성시에 상품 맵에서 주문의 총 금액을 계산한다")
    @Test
    void calculateOrder(){
        //given
        Brand brand = createBrand();
        Product product = createProduct(brand);
        Inventory inventory = createInventory(10);
        ProductOption productOption = createProductOption(product, inventory, 10000L);
        Map<ProductOption, Integer> orderProductsWithQuantity = Map.of(productOption, 2);
        User user = createUser();

        //when
        Order order = Order.create(1L, 1L, orderProductsWithQuantity,user);

        //then
        assertThat(order.getTotalPrice().getAmount().compareTo(BigDecimal.valueOf(20000))).isZero();
    }

    @DisplayName("PG사 결제 승인 실패 시 주문 상태를 PENDING으로 복구한다")
    @Test
    void rollbackStatus(){
        //given
        Brand brand = createBrand();
        Product product = createProduct(brand);
        Inventory inventory = createInventory(10);
        ProductOption productOption = createProductOption(product, inventory, 10000L);
        Map<ProductOption, Integer> orderProductsWithQuantity = Map.of(productOption, 2);
        User user = createUser();

        Order order = Order.create(1L, 1L, orderProductsWithQuantity,user);

        //when
        order.rollbackStatus();

        //then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getIsSettleable()).isFalse();
    }


    private Inventory createInventory(int stockQuantity) {
        return Inventory.builder()
                .stockQuantity(new StockQuantity(stockQuantity))
                .build();
    }

    private Brand createBrand() {
        return Brand.builder()
                .nameKo("테스트 브랜드")
                .nameEn("Test Brand")
                .status(BrandStatus.ACTIVE)
                .commissionRate(new java.math.BigDecimal("10.00"))
                .logoUrl("https://example.com/logo.jpg")
                .build();
    }

    private Product createProduct(Brand brand) {
        return Product.builder()
                .brand(brand)
                .productName("테스트 상품")
                .productInfo("테스트 상품 설명")
                .productGenderType(ProductGenderType.ALL)
                .brandName(brand.getNameKo())
                .categoryPath("상의/티셔츠")
                .isAvailable(true)
                .build();
    }

    private User createUser() {
        return User.builder()
                .userName("testUser")
                .password("password123")
                .userEmail("test@example.com")
                .contactNumber("010-1234-5678")
                .role(UserRole.USER)
                .currentAddress("서울시 강남구")
                .avatarUrl("https://example.com/avatar.jpg")
                .isActive(true)
                .build();
    }

    private ProductOption createProductOption(Product product, Inventory inventory, Long price) {
        return ProductOption.builder()
                .product(product)
                .productPrice(new Money(price))
                .inventory(inventory)
                .build();
    }
}