package com.mudosa.musinsa.coupon.presentation.dto.res;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.CouponStatus;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.model.MemberCoupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MemberCouponResDto {

    private Long memberCouponId;
    private Long couponId;
    private String couponName;

    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;

    private CouponStatus couponStatus;
    private LocalDateTime issuedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime usedAt;

    private boolean isExpired;
    private boolean isUsable;

    public static MemberCouponResDto from(MemberCoupon memberCoupon) {
        Coupon coupon = memberCoupon.getCoupon();

        return MemberCouponResDto.builder()
                .memberCouponId(memberCoupon.getId())
                .couponId(coupon.getId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .couponStatus(memberCoupon.getCouponStatus())
                .issuedAt(memberCoupon.getCreatedAt())
                .expiredAt(memberCoupon.getExpiredAt())
                .usedAt(memberCoupon.getUsedAt())
                .isExpired(memberCoupon.isExpired())
                .isUsable(memberCoupon.isUsuable())
                .build();
    }
}
