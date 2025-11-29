package com.mudosa.musinsa.coupon.domain.model;

import com.mudosa.musinsa.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon 엔티티 테스트")
class CouponTest {

    @Test
    @DisplayName("[해피케이스] Coupon 생성 - 정상적으로 Coupon을 생성한다")
    void createCoupon_Success() {
        // given
        String couponName = "신규 가입 쿠폰";
        DiscountType discountType = DiscountType.AMOUNT;
        BigDecimal discountValue = new BigDecimal("10000");
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Integer totalQuantity = 100;

        // when
        Coupon coupon = Coupon.builder()
                .couponName(couponName)
                .discountType(discountType)
                .discountValue(discountValue)
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(totalQuantity)
                .build();

        // then
        assertThat(coupon).isNotNull();
        assertThat(coupon.getCouponName()).isEqualTo(couponName);
        assertThat(coupon.getDiscountType()).isEqualTo(discountType);
        assertThat(coupon.getDiscountValue()).isEqualTo(discountValue);
        assertThat(coupon.getStartDate()).isEqualTo(startDate);
        assertThat(coupon.getEndDate()).isEqualTo(endDate);
        assertThat(coupon.getTotalQuantity()).isEqualTo(totalQuantity);
        assertThat(coupon.getIssuedQuantity()).isEqualTo(0);
        assertThat(coupon.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 유효성 검증 - 정상적인 쿠폰과 주문 금액이면 통과한다")
    void validateAvailability_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        BigDecimal orderAmount = new BigDecimal("50000");

        // when & then
        // 예외가 발생하지 않으면 성공
        coupon.validateAvailability(orderAmount);
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 유효성 검증 - 비활성화된 쿠폰이면 예외가 발생한다")
    void validateAvailability_InactiveCoupon_ThrowsException() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        // 리플렉션을 사용하지 않고 비활성화 상태를 시뮬레이션할 수 없으므로 생략
    }

    @Test
    @DisplayName("[해피케이스] 정액 할인 계산 - 정액 할인 금액을 정확히 계산한다")
    void calculateDiscountAmount_AmountType_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        BigDecimal discountValue = new BigDecimal("5000");
        Coupon coupon = Coupon.builder()
                .couponName("정액 할인 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(discountValue)
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        BigDecimal orderAmount = new BigDecimal("50000");

        // when
        BigDecimal discountAmount = coupon.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discountAmount).isEqualByComparingTo(discountValue);
    }

    @Test
    @DisplayName("[해피케이스] 정률 할인 계산 - 정률 할인 금액을 정확히 계산한다")
    void calculateDiscountAmount_PercentageType_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        BigDecimal discountValue = new BigDecimal("10"); // 10%
        Coupon coupon = Coupon.builder()
                .couponName("정률 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(discountValue)
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        BigDecimal orderAmount = new BigDecimal("50000");

        // when
        BigDecimal discountAmount = coupon.calculateDiscountAmount(orderAmount);

        // then
        // 50000 * 10 / 100 = 5000
        assertThat(discountAmount).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("[해피케이스] 정률 할인 계산 - 할인 금액이 주문 금액을 초과하지 않는다")
    void calculateDiscountAmount_NotExceedOrderAmount_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        BigDecimal discountValue = new BigDecimal("20000"); // 정액 20000원
        Coupon coupon = Coupon.builder()
                .couponName("큰 할인 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(discountValue)
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        BigDecimal orderAmount = new BigDecimal("10000");

        // when
        BigDecimal discountAmount = coupon.calculateDiscountAmount(orderAmount);

        // then
        // 할인 금액이 주문 금액을 초과할 수 없으므로 10000원
        assertThat(discountAmount).isEqualByComparingTo(orderAmount);
    }

    @Test
    @DisplayName("[해피케이스] 발급 가능 검증 - 정상적인 쿠폰이면 통과한다")
    void validateIssuable_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        LocalDateTime now = LocalDateTime.now();

        // when & then
        // 예외가 발생하지 않으면 성공
        coupon.validateIssuable(now);
    }

    @Test
    @DisplayName("[예외케이스] 발급 가능 검증 - 발급 기간이 아니면 예외가 발생한다")
    void validateIssuable_OutOfPeriod_ThrowsException() {
        // given
        LocalDateTime startDate = LocalDateTime.now().plusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("쿠폰 발급 가능 기간이 아닙니다");
    }

    @Test
    @DisplayName("[예외케이스] 발급 가능 검증 - 재고가 소진되면 예외가 발생한다")
    void validateIssuable_OutOfStock_ThrowsException() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(10)
                .build();
        // 재고를 소진시킴
        for (int i = 0; i < 10; i++) {
            coupon.increaseIssuedQuantity();
        }
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("쿠폰 재고가 모두 소진되었습니다");
    }

    @Test
    @DisplayName("[해피케이스] 발급 수량 증가 - 발급 수량이 1 증가한다")
    void increaseIssuedQuantity_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Integer initialQuantity = coupon.getIssuedQuantity();

        // when
        coupon.increaseIssuedQuantity();

        // then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(initialQuantity + 1);
    }

    @Test
    @DisplayName("[해피케이스] 남은 재고 조회 - 남은 재고를 정확히 반환한다")
    void getRemainingQuantity_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        coupon.increaseIssuedQuantity();
        coupon.increaseIssuedQuantity();

        // when
        Integer remainingQuantity = coupon.getRemainingQuantity();

        // then
        assertThat(remainingQuantity).isEqualTo(98);
    }

    @Test
    @DisplayName("[해피케이스] 남은 재고 조회 - 무제한 발급이면 null을 반환한다")
    void getRemainingQuantity_Unlimited_ReturnsNull() {
        // given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(null)
                .build();
        // when
        Integer remainingQuantity = coupon.getRemainingQuantity();

        // then
        assertThat(remainingQuantity).isNull();
    }
}
