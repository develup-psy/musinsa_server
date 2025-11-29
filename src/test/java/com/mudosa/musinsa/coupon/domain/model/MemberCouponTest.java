package com.mudosa.musinsa.coupon.domain.model;

import com.mudosa.musinsa.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemberCoupon 엔티티 테스트")
class MemberCouponTest {

    @Test
    @DisplayName("[해피케이스] MemberCoupon 발급 - 정상적으로 회원 쿠폰을 발급한다")
    void issueMemberCoupon_Success() {
        // given
        Long userId = 1L;
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

        // when
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // then
        assertThat(memberCoupon).isNotNull();
        assertThat(memberCoupon.getUserId()).isEqualTo(userId);
        assertThat(memberCoupon.getCoupon()).isEqualTo(coupon);
        assertThat(memberCoupon.getCouponStatus()).isEqualTo(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 사용 가능 여부 확인 - 사용 가능한 쿠폰이면 true를 반환한다")
    void isUsuable_Available_ReturnsTrue() {
        // given
        Long userId = 1L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when
        boolean usuable = memberCoupon.isUsuable();

        // then
        assertThat(usuable).isTrue(); // ✅ 쿠폰 종료일 + 30일이므로 사용 가능
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 사용 가능 여부 검증 - 사용 가능한 쿠폰이면 통과한다")
    void validateUsable_Available_Success() {
        // given
        Long userId = 1L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when & then
        // ✅ 예외 발생하지 않고 정상적으로 통과
        memberCoupon.validateUsable();
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 사용 - 정상적으로 쿠폰을 사용한다")
    void useCoupon_Success() {
        // given
        Long userId = 1L;
        Long orderId = 100L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when
        memberCoupon.use(orderId);

        // then
        assertThat(memberCoupon.getCouponStatus()).isEqualTo(CouponStatus.USED);
        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(orderId);
        assertThat(memberCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 만료 여부 확인 - 만료되지 않은 쿠폰이면 false를 반환한다")
    void isExpired_NotExpired_ReturnsFalse() {
        // given
        Long userId = 1L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when
        boolean expired = memberCoupon.isExpired();

        // then
        assertThat(expired).isFalse(); // ✅ 쿠폰 종료일 + 30일이므로 아직 만료되지 않음
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 사용 롤백 - 사용된 쿠폰을 롤백한다")
    void rollbackUsage_Success() {
        // given
        Long userId = 1L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when & then
        // 사용되지 않은 쿠폰은 롤백할 수 없으므로 예외 발생
        assertThatThrownBy(() -> memberCoupon.rollbackUsage())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용되지 않은 쿠폰은 롤백할 수 없습니다");
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 사용 롤백 - 사용되지 않은 쿠폰을 롤백하면 예외가 발생한다")
    void rollbackUsage_NotUsed_ThrowsException() {
        // given
        Long userId = 1L;
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
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);

        // when & then
        assertThatThrownBy(() -> memberCoupon.rollbackUsage())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용되지 않은 쿠폰은 롤백할 수 없습니다");
    }
}
