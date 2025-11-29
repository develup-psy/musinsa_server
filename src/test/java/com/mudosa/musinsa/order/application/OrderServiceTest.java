package com.mudosa.musinsa.order.application;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.order.application.dto.InsufficientStockItem;
import com.mudosa.musinsa.order.application.dto.OrderCreateItem;
import com.mudosa.musinsa.order.application.dto.PendingOrderResponse;
import com.mudosa.musinsa.order.application.dto.request.OrderCreateRequest;
import com.mudosa.musinsa.order.application.dto.response.OrderCreateResponse;
import com.mudosa.musinsa.order.application.dto.response.OrderListResponse;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderProduct;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.*;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@Transactional
class OrderServiceTest extends ServiceConfig {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OptionValueRepository optionValueRepository;

    @Autowired
    private ProductOptionValueRepository productOptionValueRepository;

    @Autowired
    private ImageRepository imageRepository;

    @DisplayName("상품 옵션과 수량을 갖는 맵으로 주문을 생성한다.")
    @Test
    void createOrder() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(10));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        OrderCreateRequest request = getOrderCreateRequest(productOption.getProductOptionId(), 2);

        //when
        OrderCreateResponse response = orderService.createPendingOrder(request, user.getId());

        //then
        Order order = orderRepository.findById(response.getOrderId()).orElseThrow();

        assertThat(order)
                .extracting("orderNo", "userId")
                .contains(response.getOrderNo(), user.getId());

        assertThat(order.getOrderProducts())
                .extracting("order")
                .containsExactly(order);
    }

    @DisplayName("주문 상품 옵션의 id가 올바르지 않을때 예외를 발생한다.")
    @Test
    void createOrderWithInvalidProductOptionId() {
        //given
        User user = userRepository.save(createUser());
        OrderCreateRequest request = getOrderCreateRequest(-10L, 2);

        //when & then
        assertThatThrownBy(() -> orderService.createPendingOrder(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("상품 옵션을 찾을 수 없습니다");
    }

    @DisplayName("주문 상품이 유효하지 않은 상품을 주문할 때 예외를 발생한다.")
    @Test
    void createOrderWithInvalidProduct() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, false)); // isAvailable = false
        Inventory inventory = inventoryRepository.save(createInventory(10));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        OrderCreateRequest request = getOrderCreateRequest(productOption.getProductOptionId(), 2);

        //when & then
        assertThatThrownBy(() -> orderService.createPendingOrder(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("현재 판매 불가능한 상품이 포함되어 있습니다");
    }

    @DisplayName("상품 옵션의 재고 수량보다 큰 수량을 차감할 경우 예외를 발생한다.")
    @Test
    void createOrderMoreThanInventory() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(10));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        OrderCreateRequest request = getOrderCreateRequest(productOption.getProductOptionId(), 20);

        //when & then
        Throwable thrown = catchThrowable(() -> orderService.createPendingOrder(request, user.getId()));

        //BusinessException 인스턴스인지 확인
        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException businessException = (BusinessException) thrown;

        //메시지 확인
        assertThat(businessException).hasMessage("재고가 부족한 상품이 있습니다");

        //예외 반환 데이터 확인
        Object data = businessException.getData();
        assertThat(data).isNotNull();
        List<InsufficientStockItem> insufficientItems = (List<InsufficientStockItem>) data;

        assertThat(insufficientItems).hasSize(1);
        assertThat(insufficientItems.get(0).getProductOptionId()).isEqualTo(productOption.getProductOptionId());
    }

    @DisplayName("결제승인 시 주문 상태가 완료 상태로 변경된다.")
    @Test
    void completeOrder() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(10));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        String orderNo = "ORD123";
        Order order = createOrder(user.getId(), orderNo, orderProducts, 40000L, OrderStatus.PENDING);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        //when
        orderService.completeOrder(orderNo);

        //then
        Order result = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @DisplayName("결제승인 시 재고를 차감한다.")
    @Test
    void completeOrderWithInventoryDeduct() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(10));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 5);
        orderProducts.add(orderProduct);

        String testOrderNo = "ORD101";
        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L, OrderStatus.PENDING);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        //when
        orderService.completeOrder(testOrderNo);

        //then
        Inventory result = inventoryRepository.findById(inventory.getInventoryId()).orElseThrow();
        assertThat(result.getStockQuantity().getValue()).isEqualTo(5);
    }

    @DisplayName("주문 재고보다 남은 재고가 더 클 경우 예외를 발생한다.")
    @Test
    void completeOrderWithManyInventoryDeduct() {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(1));
        ProductOption productOption = productOptionRepository.save(
            createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 5);
        orderProducts.add(orderProduct);

        String testOrderNo = "ORD101";
        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L, OrderStatus.PENDING);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        //when & then
        BusinessException thrown = catchThrowableOfType(() -> orderService.completeOrder(testOrderNo), BusinessException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("부족");

        List<InsufficientStockItem> insufficientItems = (List<InsufficientStockItem>) thrown.getData();
        assertThat(insufficientItems)
                .extracting(InsufficientStockItem::getProductOptionId, InsufficientStockItem::getRequestedQuantity, InsufficientStockItem::getAvailableQuantity)
                .containsExactly(
                        tuple(productOption.getProductOptionId(), 5, 1)
                );
    }

    @DisplayName("사용자가 동시에 결제하여 주문을 완료한다.")
    @ValueSource(ints = {1, 10, 100, 1000})
    @ParameterizedTest
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void orderCompleteWithConcurrent(int init) throws InterruptedException {
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));

        final int finalQuantity = 5000;
        Inventory inventory = createInventory(finalQuantity);

        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < init; i++) {
            List<OrderProduct> orderProducts = new ArrayList<>();
            OrderProduct orderProduct = createOrderProduct(productOption, 1);
            orderProducts.add(orderProduct);

            String orderNo = "ORD101_" + init + "_" + i;
            Order order = createOrder(user.getId(), orderNo, orderProducts, 40000L, OrderStatus.PENDING);
            orderProducts.forEach(op -> op.setOrderForTest(order));
            orderRepository.save(order);

            orderNos.add(orderNo);
        }

        //when
        ExecutorService executorService = Executors.newFixedThreadPool(init);
        CountDownLatch latch = new CountDownLatch(init);

        for (int i = 0; i < init; i++) {
            final String orderNo = orderNos.get(i);
            executorService.submit(() -> {
                try {
                    orderService.completeOrder(orderNo);
                } catch (Exception e) {
                    log.error("동시성 테스트 중 예외 발생", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        //then
        Inventory result = inventoryRepository.findById(inventory.getInventoryId()).orElseThrow();
        assertThat(result.getStockQuantity().getValue()).isEqualTo(finalQuantity - init);
    }

    @DisplayName("PG사 결제 승인에 성공하면 사용자의 장바구니에서 관련된 상품 옵션을 제거한다. ")
    @Test
    void deleteCartItemsAfterPaymentConfirmSuccess(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = inventoryRepository.save(createInventory(1));
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 5);
        orderProducts.add(orderProduct);

        String testOrderNo = "ORD101";
        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L, OrderStatus.COMPLETED);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        CartItem cartItem = createCartItem(productOption, user);
        cartItemRepository.save(cartItem);

        //when
        orderService.deleteCartItems(order.getId(),user.getId());

        //then
        List<CartItem> result = cartItemRepository.findAllByUserId(user.getId());
        assertThat(result).isEmpty();
    }

    @DisplayName("PG사 승인 실패 시 주문 상태를 PENDING으로 변경한다.")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateOrderStatusToPendingWhenPaymentConfirmFail(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(1);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 5);
        orderProducts.add(orderProduct);

        String testOrderNo = "ORD102";
        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L, OrderStatus.COMPLETED);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        //when
        orderService.rollbackOrder(order.getId());

        //then
        Order result = orderRepository.findById(order.getId()).orElseThrow(null);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("PG사 승인 실패 시 재고를 원복한다. ")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateInventoryQuantityToPendingWhenPaymentConfirmFail(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(10);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 5);
        orderProducts.add(orderProduct);

        String testOrderNo = "ORD102";
        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L, OrderStatus.COMPLETED);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        //when
        orderService.rollbackOrder(order.getId());

        //then
        Inventory result = inventoryRepository.findById(inventory.getInventoryId()).orElseThrow(null);
        assertThat(result.getStockQuantity().getValue()).isEqualTo(15);
    }

    @DisplayName("주문서를 조회한다.")
    @Test
    void fetchPendingOrder(){
        //given
        String orderNo = "ORD101";
        createTestData(orderNo, 100, 2);

        //when
        PendingOrderResponse result = orderService.fetchPendingOrder(orderNo);

        //then
        assertThat(result)
                .extracting("orderNo", "userName")
                .contains(orderNo, "testUser");
    }

    @DisplayName("주문번호로 주문을 삭제합니다. ")
    @Test
    void cancelOrder(){
        //given
        String orderNo = "ORD101";
        createTestData(orderNo, 100, 2);

        //when
        orderService.cancelPendingOrder(orderNo);

        //then
        assertThat(orderRepository.findByOrderNo(orderNo)).isEmpty();
    }

    @DisplayName("사용자의 주문 목록을 조회한다. ")
    @Test
    void fetchOrderList(){
        //given
        String orderNo = "ORD101";
        TestData data = createTestData(orderNo, 100, 2);

        //when
        OrderListResponse orderListResponse = orderService.fetchOrderList(data.userId);

        //then
        assertThat(orderListResponse.getOrders())
                .extracting("orderNo")
                .containsExactlyInAnyOrder(orderNo);
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

    private Product createProduct(Brand brand, boolean isValid) {
        return Product.builder()
                .brand(brand)
                .productName("테스트 상품")
                .productInfo("테스트 상품 설명")
                .productGenderType(ProductGenderType.ALL)
                .brandName(brand.getNameKo())
                .categoryPath("상의/티셔츠")
                .isAvailable(isValid)
                .build();
    }

    private ProductOption createProductOption(Product product, Inventory inventory, Long price) {
        return ProductOption.builder()
                .product(product)
                .productPrice(new Money(price))
                .inventory(inventory)
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

    private static OrderCreateRequest getOrderCreateRequest(Long productOptionId, int quantity) {
        OrderCreateItem item = OrderCreateItem.builder()
                .productOptionId(productOptionId)
                .quantity(quantity)
                .build();

        List<OrderCreateItem> items = List.of(item);

        return OrderCreateRequest.builder()
                .items(items)
                .build();
    }

    private Order createOrder(Long userId, String orderNo, List<OrderProduct> orderProducts, Long totalPrice, OrderStatus orderStatus) {
        return Order
                .builder()
                        .userId(userId)
                        .orderNo(orderNo)
                        .status(orderStatus)
                        .totalPrice(new Money(totalPrice))
                        .orderProducts(orderProducts)
                        .build();
    }

    private OrderProduct createOrderProduct(ProductOption productOption, int quantity) {
        return OrderProduct.builder()
                .productOption(productOption)
                .productQuantity(quantity)
                .productPrice(productOption.getProductPrice())
                .build();
    }

    private static CartItem createCartItem(ProductOption productOption, User user){
        return CartItem.builder()
                .user(user)
                .quantity(3)
                .productOption(productOption)
                .build();
    }

    private Order createOrder(Long userId, String orderNo, List<OrderProduct> orderProducts, Long totalPrice) {
        return Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .status(OrderStatus.PENDING)
                .totalPrice(new Money(totalPrice))
                .orderProducts(orderProducts)
                .build();
    }

    private OrderServiceTest.TestData createTestData(String orderNo, int initialStock, int orderQuantity) {
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        
        imageRepository.save(createImage(product, "https://example.com/image1.jpg", true));
        
        Inventory inventory = createInventory(initialStock);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        OptionValue sizeL = optionValueRepository.save(
                createOptionValue(ValueName.SIZE.getName(), "L")
        );
        OptionValue colorBlack = optionValueRepository.save(
                createOptionValue(ValueName.COLOR.getName(), "BLACK")
        );

        productOptionValueRepository.save(
                createProductOptionValue(productOption, sizeL)
        );
        productOptionValueRepository.save(
                createProductOptionValue(productOption, colorBlack)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, orderQuantity);
        orderProducts.add(orderProduct);

        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        return new OrderServiceTest.TestData(
                user.getId(),
                order.getId(),
                inventory.getInventoryId(),
                orderNo
        );
    }

    private static class TestData {
        final Long userId;
        final Long orderId;
        final Long inventoryId;
        final String orderNo;

        TestData(Long userId, Long orderId, Long inventoryId, String orderNo) {
            this.userId = userId;
            this.orderId = orderId;
            this.inventoryId = inventoryId;
            this.orderNo = orderNo;
        }
    }

    private OptionValue createOptionValue(String optionName, String value) {
        return OptionValue.builder()
                .optionName(optionName)
                .optionValue(value)
                .build();
    }

    private ProductOptionValue createProductOptionValue(ProductOption productOption, OptionValue optionValue) {
        return ProductOptionValue.builder()
                .productOption(productOption)
                .optionValue(optionValue)
                .build();
    }

    private Image createImage(Product product, String imageUrl, boolean isThumbnail) {
        return Image.builder()
                .product(product)
                .imageUrl(imageUrl)
                .isThumbnail(isThumbnail)
                .build();
    }
}
