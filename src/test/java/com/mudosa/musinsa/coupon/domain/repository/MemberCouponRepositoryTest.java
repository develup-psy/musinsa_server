package com.mudosa.musinsa.coupon.domain.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.model.MemberCoupon;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberCouponRepository 테스트")
class MemberCouponRepositoryTest extends ServiceConfig {

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("[해피케이스] 회원 쿠폰 저장 - 회원 쿠폰을 정상적으로 저장한다")
    void saveMemberCoupon_Success() {
        // given
        Long userId = 1L;
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, savedCoupon);

        // when
        MemberCoupon savedMemberCoupon = memberCouponRepository.save(memberCoupon);

        // then
        assertThat(savedMemberCoupon.getId()).isNotNull();
        assertThat(savedMemberCoupon.getUserId()).isEqualTo(userId);
        assertThat(savedMemberCoupon.getCoupon().getId()).isEqualTo(savedCoupon.getId());
    }

    @Test
    @DisplayName("[해피케이스] 회원별 쿠폰 조회 - 사용자 ID와 쿠폰 ID로 회원 쿠폰을 조회한다")
    void findByUserIdAndCouponId_Success() {
        // given
        Long userId = 2L;
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("조회 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, savedCoupon);
        memberCouponRepository.save(memberCoupon);

        // when
        Optional<MemberCoupon> foundMemberCoupon = memberCouponRepository.findByUserIdAndCouponId(userId, savedCoupon.getId());

        // then
        assertThat(foundMemberCoupon).isPresent();
        assertThat(foundMemberCoupon.get().getUserId()).isEqualTo(userId);
        assertThat(foundMemberCoupon.get().getCoupon().getId()).isEqualTo(savedCoupon.getId());
    }

    @Test
    @DisplayName("[해피케이스] 회원별 쿠폰 발급 개수 조회 - 사용자 ID와 쿠폰 ID로 발급 개수를 조회한다")
    void countByUserIdAndCouponId_Success() {
        // given
        Long userId = 3L;
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("카운트 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, savedCoupon);
        memberCouponRepository.save(memberCoupon);

        // when
        long count = memberCouponRepository.countByUserIdAndCouponId(userId, savedCoupon.getId());

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("[예외케이스] 회원별 쿠폰 조회 - 존재하지 않으면 빈 Optional을 반환한다")
    void findByUserIdAndCouponId_NotFound_ReturnsEmpty() {
        // given
        Long nonExistentUserId = 999999L;
        Long nonExistentCouponId = 999999L;

        // when
        Optional<MemberCoupon> foundMemberCoupon = memberCouponRepository.findByUserIdAndCouponId(nonExistentUserId, nonExistentCouponId);

        // then
        assertThat(foundMemberCoupon).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 회원별 쿠폰 발급 개수 조회 - 발급 이력이 없으면 0을 반환한다")
    void countByUserIdAndCouponId_NoIssuance_ReturnsZero() {
        // given
        Long nonExistentUserId = 999999L;
        Long nonExistentCouponId = 999999L;

        // when
        long count = memberCouponRepository.countByUserIdAndCouponId(nonExistentUserId, nonExistentCouponId);

        // then
        assertThat(count).isEqualTo(0);
    }
}
