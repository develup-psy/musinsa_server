package com.mudosa.musinsa.order.domain.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.order.application.dto.OrderFlatDto;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.application.dto.response.OrderInfo;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderProduct;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Transactional
class OrderRepositoryImplTest extends ServiceConfig {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

    private String testOrderNo = "ORD123";
    private Long userId;

    @BeforeEach
    void setUp() {
        User user = createUser();
        em.persist(user);
        userId = user.getId();

        Brand brand = createBrand();
        em.persist(brand);

        OptionValue sizeL = createOptionValue(ValueName.SIZE.getName(), "L");
        OptionValue colorBlack = createOptionValue(ValueName.COLOR.getName(), "BLACK");
        em.persist(sizeL);
        em.persist(colorBlack);

        Product product = createProduct(brand);
        em.persist(product);

        Image image = createImage(product, "https://example.com/image1.jpg", true);
        em.persist(image);

        Inventory inventory = createInventory(100);
        em.persist(inventory);

        ProductOption productOption = createProductOption(product, inventory, 20000L);
        em.persist(productOption);

        ProductOptionValue value1 = createProductOptionValue(productOption, sizeL);
        ProductOptionValue value2 = createProductOptionValue(productOption, colorBlack);
        em.persist(value1);
        em.persist(value2);

        List<OrderProduct> orderProducts = new ArrayList<>();

        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        Order order = createOrder(user.getId(), testOrderNo, orderProducts, 40000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        em.persist(order);

        em.flush();
        em.clear();
    }

    @DisplayName("주문 번호로 주문 아이템을 조회한다.")
    @Test
    void findOrderItems(){

        //when
        List<OrderItem> orderItems = orderRepository.findOrderItems(testOrderNo);

        //then
        assertThat(orderItems)
                .hasSize(1)
                .extracting(
                        "brandName",
                        "productOptionName", 
                        "quantity",
                        "imageUrl", 
                        "size", 
                        "color"
                )
                .containsExactlyInAnyOrder(
                        tuple(
                                "테스트 브랜드",
                                "테스트 상품",
                                2,
                                "https://example.com/image1.jpg",
                                "L",
                                "BLACK"
                        )
                );
    }

    @DisplayName("사용자 ID로 플랫 구조의 주문 목록을 조회한다.")
    @Test
    void findFlatOrderListWithDetails() {
        //when
        List<OrderFlatDto> flatOrders = orderRepository.findFlatOrderListWithDetails(userId);

        //then
        assertThat(flatOrders).hasSize(1);
        
        OrderFlatDto flatOrder = flatOrders.get(0);
        assertThat(flatOrder.getOrderNo()).isEqualTo(testOrderNo);
        assertThat(flatOrder.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(flatOrder.getTotalPrice()).isEqualByComparingTo("40000");
        assertThat(flatOrder.getBrandName()).isEqualTo("테스트 브랜드");
        assertThat(flatOrder.getProductName()).isEqualTo("테스트 상품");
        assertThat(flatOrder.getQuantity()).isEqualTo(2);
        assertThat(flatOrder.getImageUrl()).isEqualTo("https://example.com/image1.jpg");
        assertThat(flatOrder.getSize()).isEqualTo("L");
        assertThat(flatOrder.getColor()).isEqualTo("BLACK");
        assertThat(flatOrder.getItemAmount()).isEqualByComparingTo("20000");
    }


    @DisplayName("주문이 없는 사용자는 빈 리스트를 반환한다.")
    @Test
    void findFlatOrderListWithDetails_NoOrders() {
        //given
        User newUser = User.builder()
                .userName("newUser")
                .password("password123")
                .userEmail("new@example.com")
                .contactNumber("010-9999-9999")
                .role(UserRole.USER)
                .currentAddress("서울시 서초구")
                .avatarUrl("https://example.com/avatar2.jpg")
                .isActive(true)
                .build();
        em.persist(newUser);
        em.flush();
        em.clear();

        //when
        List<OrderFlatDto> orderInfos = orderRepository.findFlatOrderListWithDetails(newUser.getId());

        //then
        assertThat(orderInfos).isEmpty();
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

    private Brand createBrand() {
        return Brand.builder()
                .nameKo("테스트 브랜드")
                .nameEn("Test Brand")
                .status(BrandStatus.ACTIVE)
                .commissionRate(new java.math.BigDecimal("10.00"))
                .logoUrl("https://example.com/logo.jpg")
                .build();
    }

    private OptionValue createOptionValue(String optionName, String value) {
        return OptionValue.builder()
                .optionName(optionName)
                .optionValue(value)
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

    private Image createImage(Product product, String imageUrl, boolean isThumbnail) {
        return Image.builder()
                .product(product)
                .imageUrl(imageUrl)
                .isThumbnail(isThumbnail)
                .build();
    }

    private Inventory createInventory(int stockQuantity) {
        return Inventory.builder()
                .stockQuantity(new StockQuantity(stockQuantity))
                .build();
    }

    private ProductOption createProductOption(Product product, Inventory inventory, Long price) {
        return ProductOption.builder()
                .product(product)
                .productPrice(new Money(price))
                .inventory(inventory)
                .build();
    }

    private ProductOptionValue createProductOptionValue(ProductOption productOption, OptionValue optionValue) {
        return ProductOptionValue.builder()
                .productOption(productOption)
                .optionValue(optionValue)
                .build();
    }


    private Order createOrder(Long userId, String orderNo, List<OrderProduct> orderProducts, Long totalPrice) {
        return Order
                .builder()
                        .userId(userId)
                        .orderNo(orderNo)
                        .status(OrderStatus.PENDING)
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
}