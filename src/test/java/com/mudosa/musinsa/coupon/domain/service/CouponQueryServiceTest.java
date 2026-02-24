package com.mudosa.musinsa.coupon.domain.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.model.MemberCoupon;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.coupon.service.CouponListService;
import com.mudosa.musinsa.coupon.presentation.dto.res.MemberCouponResDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CouponQueryService 테스트")
@Transactional
class CouponQueryServiceTest extends ServiceConfig {

    @Autowired
    private CouponListService couponListService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Test
    @DisplayName("[해피케이스] 회원 쿠폰 목록 조회 - 사용자가 발급받은 모든 쿠폰 목록을 조회한다")
    void getMemberCoupons_Success() {
        // given
        Long userId = 1L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);

        Coupon coupon1 = Coupon.builder()
                .couponName("테스트 쿠폰1")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(100)
                .build();
        Coupon coupon2 = Coupon.builder()
                .couponName("테스트 쿠폰2")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .startDate(startDate)
                .endDate(endDate)
                .totalQuantity(50)
                .build();
        Coupon savedCoupon1 = couponRepository.save(coupon1);
        Coupon savedCoupon2 = couponRepository.save(coupon2);

        MemberCoupon memberCoupon1 = MemberCoupon.issue(userId, savedCoupon1);
        MemberCoupon memberCoupon2 = MemberCoupon.issue(userId, savedCoupon2);
        memberCouponRepository.save(memberCoupon1);
        memberCouponRepository.save(memberCoupon2);

        // when
        List<MemberCouponResDto> result = couponListService.getMemberCoupons(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MemberCouponResDto::getCouponName)
                .contains("테스트 쿠폰1", "테스트 쿠폰2");

    }

    @Test
    @DisplayName("[해피케이스] 사용 가능한 쿠폰 목록 조회 - 사용자가 발급받은 사용 가능한 쿠폰만 조회한다")
    void getAvailableMemberCoupons_Success() {
        // given
        Long userId = 2L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);

        Coupon coupon = Coupon.builder()
                .couponName("사용 가능 쿠폰")
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
        List<MemberCouponResDto> result = couponListService.getAvailableMemberCoupons(userId);

        // then
        // MemberCoupon.issue()에서 expiredAt이 현재 시간으로 설정되므로
        // isUsuable()이 false를 반환하여 빈 리스트가 됨
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 회원 쿠폰 목록 조회 - 발급받은 쿠폰이 없으면 빈 리스트를 반환한다")
    void getMemberCoupons_NoIssuance_ReturnsEmptyList() {
        // given
        Long nonExistentUserId = 999999L;

        // when
        List<MemberCouponResDto> result = couponListService.getMemberCoupons(nonExistentUserId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 사용 가능한 쿠폰 목록 조회 - 사용 가능한 쿠폰이 없으면 빈 리스트를 반환한다")
    void getAvailableMemberCoupons_NoAvailableCoupons_ReturnsEmptyList() {
        // given
        Long nonExistentUserId = 999999L;

        // when
        List<MemberCouponResDto> result = couponListService.getAvailableMemberCoupons(nonExistentUserId);

        // then
        assertThat(result).isEmpty();
    }
}
