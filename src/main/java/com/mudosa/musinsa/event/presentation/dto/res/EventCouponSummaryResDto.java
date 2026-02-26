package com.mudosa.musinsa.event.presentation.dto.res;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EventCouponSummaryResDto {

    private final Long couponId;
    private final String couponName;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final Integer totalQuantity;
    private final Integer issuedQuantity;
    private final Integer remainingQuantity;

    public static EventCouponSummaryResDto from(Coupon coupon) {
        return EventCouponSummaryResDto.builder()
                .couponId(coupon.getId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .totalQuantity(coupon.getTotalQuantity())
                .issuedQuantity(coupon.getIssuedQuantity())
                .remainingQuantity(coupon.getRemainingQuantity())
                .build();
    }

    public static EventCouponSummaryResDto empty() {
        return EventCouponSummaryResDto.builder()
                .couponId(null)
                .couponName("")
                .discountType(null)
                .discountValue(null)
                .totalQuantity(null)
                .issuedQuantity(null)
                .remainingQuantity(null)
                .build();
    }
}
