package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.CartItemCreateRequest;
import com.mudosa.musinsa.product.domain.model.Inventory;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.CartItem;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.CartItemRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CartService 테스트")
@Transactional
class CartServiceTest extends ServiceConfig {

    @Autowired
    private CartService cartService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    @DisplayName("올바른 요청으로 장바구니 상품 추가시 정상적으로 장바구니 상품이 추가된다.")
    void addCartItem() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        int quantity = 2;

        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(productOption.getProductOptionId())
            .quantity(quantity)
            .build();

        // when
        cartService.addCartItem(user.getId(), request);

        // then
        assertThat(cartItemRepository.findAllByUserId(user.getId()))
            .singleElement()
            .satisfies(cartItem -> {
                assertThat(cartItem.getProductOption().getProductOptionId()).isEqualTo(productOption.getProductOptionId());
                assertThat(cartItem.getQuantity()).isEqualTo(2);
            });
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 장바구니 상품 추가시 BusinessException이 발생한다.")
    void addCartItemsWithInvalidUser() {
        // given
        Long notExistUserId = 99999L;

        // when // then
        assertThatThrownBy(() -> cartService.addCartItem(notExistUserId, CartItemCreateRequest.builder().productOptionId(1L).quantity(1).build()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("invalidProductOptionValues")
    @DisplayName("유효하지 않는 상품 옵션으로 장바구니 상품 추가 시 BusinessException이 발생한다.")
    void addCartItemWithInvalidProductOption(Long invalidProductOptionOptionId, ErrorCode expectedErrorCode) {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(invalidProductOptionOptionId)
            .quantity(1)
            .build();

        // when // then
        assertThatThrownBy(() -> cartService.addCartItem(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expectedErrorCode);
    }

    static Stream<Arguments> invalidProductOptionValues() {
        return Stream.of(
            Arguments.of(null, ErrorCode.PRODUCT_OPTION_NOT_FOUND),
            Arguments.of(99999L, ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        );
    }

    @Test
    @DisplayName("장바구니 상품 추가 시 수량이 null이면 CART_ITEM_STOCK_QUANTITY_REQUIRED가 발생한다.")
    void addCartItemWithNullQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();

        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(productOption.getProductOptionId())
            .quantity(null)
            .build();

        // when // then
        assertThatThrownBy(() -> cartService.addCartItem(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_STOCK_QUANTITY_REQUIRED);
    }

    @Test
    @DisplayName("재고의 stockQuantity가 null이면 PRODUCT_OPTION_OUT_OF_STOCK가 발생한다.")
    void addCartItemWithNullStockQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        ReflectionTestUtils.setField(productOption.getInventory(), "stockQuantity", null);

        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(productOption.getProductOptionId())
            .quantity(1)
            .build();

        // when // then
        assertThatThrownBy(() -> cartService.addCartItem(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_OPTION_OUT_OF_STOCK);
    }

    @ParameterizedTest
    @ValueSource(booleans = false)
    @DisplayName("상품 장바구니 추가 시 사용 불가능한(비활성화된) 상품으로 요청할 경우 BusinessException이 발생한다.")
    void addCartItemWithUnavailableProduct(Boolean isAvailable) {
        // given
        User user = userRepository.save(User.create("user2", "pw", "u2@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        ReflectionTestUtils.setField(productOption.getProduct(), "isAvailable", isAvailable);

        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(productOption.getProductOptionId())
            .quantity(1)
            .build();

        // when // then
        assertThatThrownBy(() -> cartService.addCartItem(user.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("올바른 요청으로 장바구니 상품 목록을 조회시 정상적으로 장바구니 상품 목록이 반환된다.")
    void getCartItems() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption1 = prepareProductOption();
        ProductOption productOption2 = prepareProductOption();
        cartItemRepository.saveAll(List.of(
            CartItem.builder().user(user).productOption(productOption1).quantity(2).build(),
            CartItem.builder().user(user).productOption(productOption2).quantity(3).build()
        ));

        // when
        var cartItems = cartService.getCartItems(user.getId());

        // then
        assertThat(cartItems)
            .hasSize(2)
            .extracting("productOptionId")
            .containsExactlyInAnyOrder(
                productOption1.getProductOptionId(),
                productOption2.getProductOptionId()
            );
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 장바구니 목록 조회 시 BusinessException이 발생한다.")
    void getCartItemsWithInvalidUser() {
        // given
        Long notExistUserId = 99999L;

        // when // then
        assertThatThrownBy(() -> cartService.getCartItems(notExistUserId))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("올바른 요청으로 장바구니 상품 수량 변경시 정상적으로 장바구니 상품 수량이 변경된다.")
    void updateCartItemQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        int newQuantity = 5;

        // when
        cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), newQuantity);

        // then
        CartItem updatedCartItem = cartItemRepository.findById(cartItem.getCartItemId()).orElseThrow();
        assertThat(updatedCartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("상품 수량을 0 이하로 변경 요청 시 BusinessException이 발생한다.")
    void updateCartItemQuantityWithInvalidQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        int invalidQuantity = 0;

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), invalidQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_CART_ITEM_UPDATE_VALUE);
    }

    @Test
    @DisplayName("장바구니 상품 수량 변경 시 존재하지 않는 장바구니 항목으로 요청할 경우 BusinessException이 발생한다.")
    void updateCartItemQuantityWithInvalidCartItem() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        Long notExistCartItemId = 99999L;
        int newQuantity = 3;

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), notExistCartItemId, newQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 장바구니 상품 수량 변경 시 BusinessException이 발생한다.")
    void updateCartItemQuantityWithDifferentUser() {
        // given
        User user1 = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        User user2 = userRepository.save(User.create("user2", "pw", "u2@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user1)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        int newQuantity = 3;

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user2.getId(), cartItem.getCartItemId(), newQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_ONLY_ACCESS_PERSONAL);
    }

    @Test
    @DisplayName("장바구니 수량 변경 시 상품 옵션이 없으면 BusinessException이 발생한다.")
    void updateCartItemQuantityWithMissingProductOption() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption validProductOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(validProductOption)
                .quantity(2)
                .build()
        );
        int newQuantity = 3;
        ReflectionTestUtils.setField(cartItem, "productOption", null);

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), newQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
    }

    @ParameterizedTest
    @DisplayName("상품 장바구니 수량 변경 시 사용 불가능한(비활성화된) 상품으로 요청할 경우 BusinessException이 발생한다.")
    @ValueSource(booleans = false)
    void updateCartItemQuantityWithUnavailableProduct(Boolean isAvailable) {
        // given
        User user = userRepository.save(User.create("user2", "pw", "u2@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        ReflectionTestUtils.setField(productOption.getProduct(), "isAvailable", isAvailable);
        int newQuantity = 3;

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), newQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("동일 옵션을 다시 담으면 기존 장바구니 수량이 누적된다.")
    void addCartItemAccumulatesQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption(); // 재고 10

        cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(3)
                .build()
        );

        CartItemCreateRequest request = CartItemCreateRequest.builder()
            .productOptionId(productOption.getProductOptionId())
            .quantity(2)
            .build();

        // when
        cartService.addCartItem(user.getId(), request);

        // then
        assertThat(cartItemRepository.findAllByUserId(user.getId()))
            .singleElement()
            .satisfies(cartItem -> assertThat(cartItem.getQuantity()).isEqualTo(5));
    }

    @Test
    @DisplayName("상품 옵션 ID 목록으로 장바구니 항목을 삭제할 수 있다.")
    void deleteCartItemsByProductOptions() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption option1 = prepareProductOption();
        ProductOption option2 = prepareProductOption();
        cartItemRepository.saveAll(List.of(
            CartItem.builder().user(user).productOption(option1).quantity(1).build(),
            CartItem.builder().user(user).productOption(option2).quantity(1).build()
        ));

        // when
        cartService.deleteCartItemsByProductOptions(user.getId(), List.of(option1.getProductOptionId()));

        // then
        assertThat(cartItemRepository.findAllByUserId(user.getId()))
            .singleElement()
            .satisfies(remaining -> assertThat(remaining.getProductOption().getProductOptionId())
                .isEqualTo(option2.getProductOptionId()));
    }

    @ParameterizedTest
    @DisplayName("올바르지 않는 상품 옵션의 수량으로 장바구니 수량 변경 시 BusinessException이 발생한다.")
    @MethodSource("invalidQuantities")
    void updateCartItemQuantityWithInvalidQuantity(Integer invalidQuantity, ErrorCode expectedErrorCode) {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), invalidQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expectedErrorCode);
    }

    static Stream<Arguments> invalidQuantities() {
        return Stream.of(
            Arguments.of(null, ErrorCode.CART_ITEM_STOCK_QUANTITY_REQUIRED),
            Arguments.of(0, ErrorCode.INVALID_CART_ITEM_UPDATE_VALUE),
            Arguments.of(-3, ErrorCode.INVALID_CART_ITEM_UPDATE_VALUE)
        );
    }

    @Test
    @DisplayName("요청한 상품 옵션의 수량이 재고 수량을 초과할 경우 장바구니 수량 변경 시 BusinessException이 발생한다.")
    void updateCartItemQuantityWithExceedingQuantity() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        int newQuantity = 20; // 재고 수량을 초과하는 값

        // when // then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(user.getId(), cartItem.getCartItemId(), newQuantity))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("올바른 요청으로 장바구니 상품 개별 삭제시 정상적으로 장바구니 상품이 삭제된다.")
    void deleteCartItem() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(2)
                .build()
        );

        // when
        cartService.deleteCartItem(user.getId(), cartItem.getCartItemId());

        // then
        assertThat(cartItemRepository.findById(cartItem.getCartItemId())).isEmpty();
    }

    @Test
    @DisplayName("장바구니 상품 삭제 시 존재하지 않는 장바구니 항목으로 요청할 경우 BusinessException이 발생한다.")
    void deleteCartItemWithInvalidCartItem() {
        // given
        User user = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        Long notExistCartItemId = 99999L;

        // when // then
        assertThatThrownBy(() -> cartService.deleteCartItem(user.getId(), notExistCartItemId))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 장바구니 상품 삭제 시 BusinessException이 발생한다.")
    void deleteCartItemWithDifferentUser() {
        // given
        User user1 = userRepository.save(User.create("user1", "pw", "u1@test.com", UserRole.USER, null, null, null));
        User user2 = userRepository.save(User.create("user2", "pw", "u2@test.com", UserRole.USER, null, null, null));
        ProductOption productOption = prepareProductOption();
        CartItem cartItem = cartItemRepository.save(
            CartItem.builder()
                .user(user1)
                .productOption(productOption)
                .quantity(2)
                .build()
        );
        
        // when // then
        assertThatThrownBy(() -> cartService.deleteCartItem(user2.getId(), cartItem.getCartItemId()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.CART_ITEM_ONLY_ACCESS_PERSONAL);
    }

    // 상품 및 상품 옵션 준비
    private ProductOption prepareProductOption() {
        Brand brand = brandRepository.save(Brand.create("브랜드", "BRAND", BigDecimal.ZERO));
        Product product = Product.create(
            brand,
            "상품명",
            "상품 정보",
            ProductGenderType.ALL,
            brand.getNameKo(),
            "상의/티셔츠",
            true,
            BigDecimal.ZERO,
            List.of(),
            List.of()
        );

        ProductOption productOption = ProductOption.create(
            product,
            new Money(10000L),
            Inventory.create(new StockQuantity(10))
        );
        product.addProductOption(productOption);

        productRepository.save(product);
        return productOption;
    }
}
