package com.mudosa.musinsa.coupon.repository;

import com.mudosa.musinsa.order.application.dto.OrderMemberCoupon;
import com.mudosa.musinsa.order.application.dto.QOrderMemberCoupon;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.mudosa.musinsa.coupon.model.QCoupon.coupon;
import static com.mudosa.musinsa.coupon.model.QMemberCoupon.memberCoupon;


@RequiredArgsConstructor
public class MemberCouponRepositoryImpl implements MemberCouponRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<OrderMemberCoupon> findOrderMemberCouponsByUserId(Long userId) {
        return queryFactory
                .select(new QOrderMemberCoupon(
                        coupon.id,
                        coupon.couponName,
                        coupon.discountType,
                        coupon.discountValue
                ))
                .from(memberCoupon)
                .join(memberCoupon.coupon, coupon)
                .where(memberCoupon.userId.eq(userId))
                .fetch();
    }
}
