package com.mudosa.musinsa.coupon.domain.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CouponRepository 테스트")
@Transactional
class CouponRepositoryTest extends ServiceConfig {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("[해피케이스] 쿠폰 저장 - 쿠폰을 정상적으로 저장한다")
    void saveCoupon_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("신규 가입 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);

        // then
        assertThat(savedCoupon.getId()).isNotNull();
        assertThat(savedCoupon.getCouponName()).isEqualTo("신규 가입 쿠폰");
        assertThat(savedCoupon.getDiscountType()).isEqualTo(DiscountType.AMOUNT);
        assertThat(savedCoupon.getDiscountValue()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("[해피케이스] 쿠폰 조회 - ID로 쿠폰을 조회한다")
    void findCouponById_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(50)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        Optional<Coupon> foundCoupon = couponRepository.findById(savedCoupon.getId());

        // then
        assertThat(foundCoupon).isPresent();
        assertThat(foundCoupon.get().getId()).isEqualTo(savedCoupon.getId());
        assertThat(foundCoupon.get().getCouponName()).isEqualTo("테스트 쿠폰");
    }

    @Test
    @DisplayName("[해피케이스] 비관적 락으로 쿠폰 조회 - ID로 쿠폰을 조회하고 락을 건다")
    void findByIdForUpdate_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("락 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        Optional<Coupon> foundCoupon = couponRepository.findByIdForUpdate(savedCoupon.getId());

        // then
        assertThat(foundCoupon).isPresent();
        assertThat(foundCoupon.get().getId()).isEqualTo(savedCoupon.getId());
        assertThat(foundCoupon.get().getCouponName()).isEqualTo("락 테스트 쿠폰");
    }

    @Test
    @DisplayName("[예외케이스] 쿠폰 조회 - 존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void findCouponById_NotFound_ReturnsEmpty() {
        // given
        Long nonExistentId = 999999L;

        // when
        Optional<Coupon> foundCoupon = couponRepository.findById(nonExistentId);

        // then
        assertThat(foundCoupon).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 비관적 락으로 쿠폰 조회 - 존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void findByIdForUpdate_NotFound_ReturnsEmpty() {
        // given
        Long nonExistentId = 999999L;

        // when
        Optional<Coupon> foundCoupon = couponRepository.findByIdForUpdate(nonExistentId);

        // then
        assertThat(foundCoupon).isEmpty();
    }
}
