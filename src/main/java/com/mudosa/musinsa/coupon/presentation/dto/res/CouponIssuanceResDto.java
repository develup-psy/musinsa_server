package com.mudosa.musinsa.coupon.presentation.dto.res;

import java.time.LocalDateTime;

public record CouponIssuanceResDto(
        Long memberCouponId,
        Long couponId,
        LocalDateTime expiredAt,
        LocalDateTime issuedAt,
        boolean duplicate
) {
    public static CouponIssuanceResDto issued(
            Long memberCouponId,
            Long couponId,
            LocalDateTime expiredAt,
            LocalDateTime issuedAt
    ) {
        return new CouponIssuanceResDto(memberCouponId, couponId, expiredAt, issuedAt, false);
    }

    public static CouponIssuanceResDto duplicate(
            Long memberCouponId,
            Long couponId,
            LocalDateTime expiredAt,
            LocalDateTime issuedAt
    ) {
        return new CouponIssuanceResDto(memberCouponId, couponId, expiredAt, issuedAt, true);
    }
}