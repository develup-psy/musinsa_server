package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventOption;
import com.mudosa.musinsa.event.repository.EventOptionRepository;
import com.mudosa.musinsa.event.repository.EventRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventCouponService 테스트")
@Transactional
class EventCouponServiceTest extends ServiceConfig {

    @Autowired
    private EventCouponService eventCouponService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventOptionRepository eventOptionRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("[해피케이스] 쿠폰 발급 - 정상적으로 이벤트 쿠폰을 발급한다")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;

        // Brand 생성
        Brand brand = Brand.builder()
                .nameKo("테스트 브랜드")
                .nameEn("Test Brand")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        // Category 생성
        Category category = Category.builder()
                .categoryName("테스트 카테고리")
                .parent(null)
                .build();
        categoryRepository.save(category);

        // Product 생성
        Product product = Product.builder()
                .productName("테스트 상품")
                .productInfo("테스트 상품 설명")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        // ProductOption 생성
        Money price = new Money(new BigDecimal("100000"));
        StockQuantity stockQuantity = new StockQuantity(50);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(
                product,
                price,
                inventory
        );
        productOptionRepository.save(productOption);

        // Coupon 생성
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("이벤트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        // Event 생성
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)
                .build();
        event.open(); // 이벤트를 OPEN 상태로 변경
        eventRepository.save(event);

        // EventOption 생성
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(50)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        // when
        EventCouponService.EventCouponIssueResult result = eventCouponService.issueCoupon(
                event.getId(),
                userId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.memberCouponId()).isNotNull();
        assertThat(result.couponId()).isEqualTo(coupon.getId());
        assertThat(result.duplicate()).isFalse();
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 중복 발급 - 이미 발급받은 쿠폰은 중복 처리된다")
    void issueCoupon_Duplicate_Success() {
        // given
        Long userId = 2L;

        // Brand 생성
        Brand brand = Brand.builder()
                .nameKo("테스트 브랜드2")
                .nameEn("Test Brand 2")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        // Category 생성
        Category category = Category.builder()
                .categoryName("테스트 카테고리2")
                .parent(null)
                .build();
        categoryRepository.save(category);

        // Product 생성
        Product product = Product.builder()
                .productName("테스트 상품2")
                .productInfo("테스트 상품 설명2")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        // ProductOption 생성
        Money price = new Money(new BigDecimal("100000"));
        StockQuantity stockQuantity = new StockQuantity(50);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(
                product,
                price,
                inventory
        );
        productOptionRepository.save(productOption);

        // Coupon 생성
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("중복 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        // Event 생성
        Event event = Event.builder()
                .title("중복 테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(2)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)  // ✅ 수정: coupon 추가
                .build();

        event.open();
        eventRepository.save(event);

        // EventOption 생성
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)  // ✅ 수정
                .eventPrice(new BigDecimal("80000"))  // ✅ 수정
                .eventStock(50)  // ✅ 수정
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        // 첫 번째 발급
        eventCouponService.issueCoupon(event.getId(), userId);

        // when
        // 두 번째 발급 시도
        EventCouponService.EventCouponIssueResult result = eventCouponService.issueCoupon(
                event.getId(),
                userId  // ✅ 수정
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.duplicate()).isTrue();
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 발급 - 존재하지 않는 이벤트 옵션이면 예외가 발생한다")
    void issueCoupon_EventOptionNotFound_ThrowsException() {
        // given
        Long userId = 3L;
        Long nonExistentEventId = 999999L;

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(
                nonExistentEventId,
                userId
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 발급 - 이벤트가 OPEN 상태가 아니면 예외가 발생한다")
    void issueCoupon_EventNotOpen_ThrowsException() {
        // given
        Long userId = 4L;

        // Brand 생성
        Brand brand = Brand.builder()
                .nameKo("테스트 브랜드3")
                .nameEn("Test Brand 3")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        // Category 생성
        Category category = Category.builder()
                .categoryName("테스트 카테고리3")
                .parent(null)
                .build();
        categoryRepository.save(category);

        // Product 생성
        Product product = Product.builder()
                .productName("테스트 상품3")
                .productInfo("테스트 상품 설명3")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        // ProductOption 생성
        Money price = new Money(new BigDecimal("100000"));
        StockQuantity stockQuantity = new StockQuantity(50);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(
                product,
                price,
                inventory
        );
        productOptionRepository.save(productOption);

        // Coupon 생성
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰3")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        // Event 생성 (DRAFT 상태로 유지)
        Event event = Event.builder()
                .title("DRAFT 상태 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)
                .build();
        // open() 호출하지 않음 - DRAFT 상태 유지
        eventRepository.save(event);

        // EventOption 생성
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(50)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(
                event.getId(),
                userId
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 발급 - 재고가 부족하면 예외가 발생한다")
    void issueCoupon_InsufficientStock_ThrowsException() {
        // given
        Long userId1 = 5L;
        Long userId2 = 6L;

        // Brand 생성
        Brand brand = Brand.builder()
                .nameKo("테스트 브랜드4")
                .nameEn("Test Brand 4")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        // Category 생성
        Category category = Category.builder()
                .categoryName("테스트 카테고리4")
                .parent(null)
                .build();
        categoryRepository.save(category);

        // Product 생성
        Product product = Product.builder()
                .productName("테스트 상품4")
                .productInfo("테스트 상품 설명4")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        // ProductOption 생성
        Money price = new Money(new BigDecimal("100000"));
        StockQuantity stockQuantity = new StockQuantity(50);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(
                product,
                price,
                inventory
        );
        productOptionRepository.save(productOption);

        // Coupon 생성
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("재고 부족 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(1)  // ✅ 수정: 쿠폰 재고도 1개로
                .build();
        couponRepository.save(coupon);

        // Event 생성
        Event event = Event.builder()
                .title("재고 부족 테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(10)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)
                .build();
        event.open();
        eventRepository.save(event);

        // EventOption 생성 (재고 1개만)
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(1)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        // 첫 번째 사용자가 발급받음
        eventCouponService.issueCoupon(event.getId(), userId1);

        // when & then
        // 두 번째 사용자가 발급 시도 - 재고 부족으로 실패
        assertThatThrownBy(() -> eventCouponService.issueCoupon(
                event.getId(),
                userId2
        ))
                .isInstanceOf(BusinessException.class);
    }
}