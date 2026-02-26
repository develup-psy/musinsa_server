package com.mudosa.musinsa.coupon.domain.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.presentation.dto.res.CouponIssuanceResDto;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.coupon.service.CouponIssuanceService;
import com.mudosa.musinsa.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("CouponIssuanceService 테스트")
@Transactional
class CouponIssuanceServiceTest extends ServiceConfig {

    @Autowired
    private CouponIssuanceService couponIssuanceService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Test
    @DisplayName("[해피케이스] 쿠폰 발급 - 정상적으로 쿠폰을 발급한다")
    void issueCoupon_WithLock_Success() {
        // given
        Long userId = 1L;
        Long productId = 100L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("발급 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        CouponIssuanceResDto result = couponIssuanceService.issueCoupon(userId, savedCoupon.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.memberCouponId()).isNotNull();
        assertThat(result.couponId()).isEqualTo(savedCoupon.getId());
        assertThat(result.duplicate()).isFalse();
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 중복 발급 - 이미 발급된 쿠폰이면 중복 발급으로 처리한다")
    void issueCoupon_WithLock_Duplicate_Success() {
        // given
        Long userId = 2L;
        Long productId = 100L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("중복 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // 첫 번째 발급
        couponIssuanceService.issueCoupon(userId, savedCoupon.getId());

        // when
        // 두 번째 발급 시도
        CouponIssuanceResDto result = couponIssuanceService.issueCoupon(userId, savedCoupon.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.duplicate()).isTrue();
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 발급 - 존재하지 않는 쿠폰이면 예외가 발생한다")
    void issueCoupon_CouponWithLockNotFound_ThrowsException() {
        // given
        Long userId = 3L;
        Long productId = 100L;
        Long nonExistentCouponId = 999999L;

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.issueCoupon(userId, nonExistentCouponId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 발급 - 발급 기간이 아니면 예외가 발생한다")
    void issueCoupon_WithLock_OutOfPeriod_ThrowsException() {
        // given
        Long userId = 4L;
        Long productId = 100L;
        LocalDateTime startDate = LocalDateTime.now().plusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("기간 외 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.issueCoupon(userId, savedCoupon.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[해피케이스] 발급된 쿠폰 조회 - 발급된 쿠폰을 조회한다")
    void findIssuedCoupon_Success() {
        // given
        Long userId = 5L;
        Long productId = 100L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("조회 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        couponIssuanceService.issueCoupon(userId, savedCoupon.getId());

        // when
        Optional<CouponIssuanceResDto> result = couponIssuanceService.findIssuedCoupon(userId, savedCoupon.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().couponId()).isEqualTo(savedCoupon.getId());
    }

    @Test
    @DisplayName("[해피케이스] 발급 개수 조회 - 사용자별 쿠폰 발급 개수를 조회한다")
    void countIssuedByUser_Success() {
        // given
        Long userId = 6L;
        Long productId = 100L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .couponName("카운트 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        couponIssuanceService.issueCoupon(userId, savedCoupon.getId());

        // when
        long count = couponIssuanceService.countIssuedByUser(userId, savedCoupon.getId());

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("[예외케이스] 발급된 쿠폰 조회 - 발급 이력이 없으면 빈 Optional을 반환한다")
    void findIssuedCoupon_NotFound_ReturnsEmpty() {
        // given
        Long nonExistentUserId = 999999L;
        Long nonExistentCouponId = 999999L;

        // when
        Optional<CouponIssuanceResDto> result = couponIssuanceService.findIssuedCoupon(nonExistentUserId, nonExistentCouponId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 발급 개수 조회 - 발급 이력이 없으면 0을 반환한다")
    void countIssuedByUser_NoIssuance_ReturnsZero() {
        // given
        Long nonExistentUserId = 999999L;
        Long nonExistentCouponId = 999999L;

        // when
        long count = couponIssuanceService.countIssuedByUser(nonExistentUserId, nonExistentCouponId);

        // then
        assertThat(count).isEqualTo(0);
    }
}

