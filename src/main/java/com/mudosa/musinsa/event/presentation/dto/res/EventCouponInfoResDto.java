package com.mudosa.musinsa.event.presentation.dto.res;

import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.event.service.EventCouponService;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class EventCouponInfoResDto {

    private final Long couponId;
    private final String couponName;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minOrderAmount;
    private final BigDecimal maxDiscountAmount;
    private final Integer totalQuantity;
    private final Integer issuedQuantity;
    private final Integer remainingQuantity;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;
    private final Integer limitPerUser;

    public static EventCouponInfoResDto from(EventCouponService.EventCouponInfoResult result) {
        return EventCouponInfoResDto.builder()
                .couponId(result.couponId())
                .couponName(result.couponName())
                .discountType(result.discountType())
                .discountValue(result.discountValue())
                .minOrderAmount(result.minOrderAmount())
                .maxDiscountAmount(result.maxDiscountAmount())
                .totalQuantity(result.totalQuantity())
                .issuedQuantity(result.issuedQuantity())
                .remainingQuantity(result.remainingQuantity())
                .startedAt(result.startedAt())
                .endedAt(result.endedAt())
                .limitPerUser(result.limitPerUser())
                .build();
    }
}
