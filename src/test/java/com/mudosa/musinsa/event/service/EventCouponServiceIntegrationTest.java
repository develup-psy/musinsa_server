package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventOption;
import com.mudosa.musinsa.event.repository.EventOptionRepository;
import com.mudosa.musinsa.event.repository.EventRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
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

/**
 * EventCouponService 통합 테스트
 *
 * 목적: 복잡한 이벤트 시나리오 검증 (동시성 제외)
 * - 이벤트 라이프사이클 시나리오
 * - 사용자 제한 검증
 * - 여러 엔티티 간 상호작용
 *
 * 주의: 동시성 테스트는 CouponIssuanceServiceConcurrencyTest에서만!
 */
@DisplayName("EventCouponService 통합 테스트")
@Transactional
class EventCouponServiceIntegrationTest extends ServiceConfig {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("[통합] 사용자 발급 제한 초과 시 예외 발생")
    void issueCoupon_ExceedsUserLimit_ThrowsException() {
        // given
        Long userId = 100L;
        int USER_LIMIT = 2; // 사용자당 2개까지만

        TestData testData = createTestData(100, USER_LIMIT);

        // 첫 번째 발급 성공
        eventCouponService.issueCoupon(testData.eventId, userId);

        // 두 번째 발급 - 중복이므로 duplicate=true로 반환
        EventCouponService.EventCouponIssueResult secondResult =
                eventCouponService.issueCoupon(testData.eventId, userId);
        assertThat(secondResult.duplicate()).isTrue();

        // when & then
        // 세 번째 발급 시도 - 제한 초과로 예외 발생
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 이벤트 기간이 종료되면 쿠폰 발급 실패")
    void issueCoupon_EventExpired_ThrowsException() {
        // given
        Long userId = 101L;

        // 이미 종료된 이벤트 생성
        LocalDateTime startDate = LocalDateTime.now().minusDays(10);
        LocalDateTime endDate = LocalDateTime.now().minusDays(1); // 어제 종료

        TestData testData = createTestDataWithDates(100, 1, startDate, endDate);

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 이벤트 시작 전에는 쿠폰 발급 실패")
    void issueCoupon_EventNotStarted_ThrowsException() {
        // given
        Long userId = 102L;

        // 미래에 시작되는 이벤트
        LocalDateTime startDate = LocalDateTime.now().plusDays(1); // 내일 시작
        LocalDateTime endDate = LocalDateTime.now().plusDays(10);

        TestData testData = createTestDataWithDates(100, 1, startDate, endDate);

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 쿠폰이 할당되지 않은 이벤트는 발급 실패")
    void issueCoupon_NoCoupon_ThrowsException() {
        // given
        Long userId = 103L;

        TestData testData = createTestDataWithoutCoupon();

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 여러 사용자가 순차적으로 쿠폰 발급 성공")
    void issueCoupon_MultipleUsersSequential_Success() {
        // given
        int COUPON_STOCK = 10;
        TestData testData = createTestData(COUPON_STOCK, 1);

        // when
        for (long userId = 1; userId <= COUPON_STOCK; userId++) {
            EventCouponService.EventCouponIssueResult result =
                    eventCouponService.issueCoupon(testData.eventId, userId);

            // then
            assertThat(result.duplicate()).isFalse();
        }

        // 재고 확인
        entityManager.flush();
        entityManager.clear();

        Coupon updatedCoupon = couponRepository.findById(testData.couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(COUPON_STOCK);
    }

    @Test
    @DisplayName("[통합] 재고 소진 후 발급 시도 시 예외 발생")
    void issueCoupon_StockDepleted_ThrowsException() {
        // given
        int COUPON_STOCK = 3;
        TestData testData = createTestData(COUPON_STOCK, 1);

        // 재고 모두 소진
        for (long userId = 1; userId <= COUPON_STOCK; userId++) {
            eventCouponService.issueCoupon(testData.eventId, userId);
        }

        // when & then
        // 재고 소진 후 추가 발급 시도
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 이벤트 상태가 DRAFT면 쿠폰 발급 실패")
    void issueCoupon_EventDraft_ThrowsException() {
        // given
        Long userId = 104L;
        TestData testData = createDraftEventTestData();

        // when & then
        assertThatThrownBy(() -> eventCouponService.issueCoupon(testData.eventId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[통합] 이벤트 쿠폰 정보 조회 성공")
    void getEventCouponInfo_Success() {
        TestData testData = createTestData(50, 2);

        EventCouponService.EventCouponInfoResult result =
                eventCouponService.getEventCoupon(testData.eventId);

        Coupon coupon = couponRepository.findById(testData.couponId).orElseThrow();

        assertThat(result).isNotNull();
        assertThat(result.couponId()).isEqualTo(coupon.getId());
        assertThat(result.couponName()).isEqualTo(coupon.getCouponName());
        assertThat(result.discountType()).isEqualTo(coupon.getDiscountType());
        assertThat(result.discountValue()).isEqualByComparingTo(coupon.getDiscountValue());
        assertThat(result.totalQuantity()).isEqualTo(coupon.getTotalQuantity());
        assertThat(result.limitPerUser()).isEqualTo(2);
        assertThat(result.remainingQuantity()).isEqualTo(coupon.getRemainingQuantity());
    }

    // ===== 헬퍼 메서드 =====

    private TestData createTestData(int couponStock, int limitPerUser) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        return createTestDataWithDates(couponStock, limitPerUser, startDate, endDate);
    }

    private TestData createTestDataWithDates(int couponStock, int limitPerUser,
                                             LocalDateTime startDate, LocalDateTime endDate) {
        Brand brand = Brand.builder()
                .nameKo("통합테스트 브랜드")
                .nameEn("Integration Test Brand")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        Category category = Category.builder()
                .categoryName("통합테스트 카테고리")
                .parent(null)
                .build();
        categoryRepository.save(category);

        Product product = Product.builder()
                .productName("통합테스트 상품")
                .productInfo("테스트용 상품 정보")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        Money price = new Money(new BigDecimal("100000.00"));
        StockQuantity stockQuantity = new StockQuantity(1000);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(product, price, inventory);
        productOptionRepository.save(productOption);

        Coupon coupon = Coupon.builder()
                .couponName("통합테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(couponStock)
                .build();
        couponRepository.save(coupon);

        Event event = Event.builder()
                .title("통합테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(limitPerUser)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)
                .build();
        event.open();
        eventRepository.save(event);

        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(couponStock)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        return new TestData(event.getId(), eventOption.getId(),
                productOption.getProductOptionId(), coupon.getId());
    }

    private TestData createTestDataWithoutCoupon() {
        Brand brand = Brand.builder()
                .nameKo("쿠폰 없는 브랜드")
                .nameEn("No Coupon Brand")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        Category category = Category.builder()
                .categoryName("쿠폰 없는 카테고리")
                .parent(null)
                .build();
        categoryRepository.save(category);

        Product product = Product.builder()
                .productName("쿠폰 없는 상품")
                .productInfo("테스트용 상품 정보")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        Money price = new Money(new BigDecimal("100000.00"));
        StockQuantity stockQuantity = new StockQuantity(1000);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(product, price, inventory);
        productOptionRepository.save(productOption);

        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);

        Event event = Event.builder()
                .title("쿠폰 없는 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(null) // 쿠폰 없음!
                .build();
        event.open();
        eventRepository.save(event);

        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(100)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        return new TestData(event.getId(), eventOption.getId(),
                productOption.getProductOptionId(), null);
    }

    private TestData createDraftEventTestData() {
        Brand brand = Brand.builder()
                .nameKo("DRAFT 브랜드")
                .nameEn("Draft Brand")
                .commissionRate(new BigDecimal("10.00"))
                .status(BrandStatus.ACTIVE)
                .build();
        brandRepository.save(brand);

        Category category = Category.builder()
                .categoryName("DRAFT 카테고리")
                .parent(null)
                .build();
        categoryRepository.save(category);

        Product product = Product.builder()
                .productName("DRAFT 상품")
                .productInfo("테스트용 상품 정보")
                .brand(brand)
                .brandName(brand.getNameKo())
                .categoryPath(category.buildPath())
                .productGenderType(ProductGenderType.ALL)
                .isAvailable(true)
                .build();
        productRepository.save(product);

        Money price = new Money(new BigDecimal("100000.00"));
        StockQuantity stockQuantity = new StockQuantity(1000);
        Inventory inventory = Inventory.create(stockQuantity);

        ProductOption productOption = ProductOption.create(product, price, inventory);
        productOptionRepository.save(productOption);

        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);

        Coupon coupon = Coupon.builder()
                .couponName("DRAFT 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        Event event = Event.builder()
                .title("DRAFT 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startDate)
                .endedAt(endDate)
                .coupon(coupon)
                .build();
        // open() 호출 안 함! DRAFT 상태 유지
        eventRepository.save(event);

        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(productOption)
                .eventPrice(new BigDecimal("80000"))
                .eventStock(100)
                .build();
        eventOptionRepository.save(eventOption);

        entityManager.flush();
        entityManager.clear();

        return new TestData(event.getId(), eventOption.getId(),
                productOption.getProductOptionId(), coupon.getId());
    }

    private record TestData(
            Long eventId,
            Long eventOptionId,
            Long productOptionId,
            Long couponId
    ) {}
}
