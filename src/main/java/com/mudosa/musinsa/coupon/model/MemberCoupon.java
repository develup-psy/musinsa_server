package com.mudosa.musinsa.coupon.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_coupon",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_coupon_user_coupon", columnNames = {"user_id", "coupon_id"})
        },
        indexes = {
                @Index(name = "idx_member_coupon_user_status", columnList = "user_id, coupon_status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_coupon_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_status", nullable = false)
    private CouponStatus couponStatus = CouponStatus.AVAILABLE;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;


    public static MemberCoupon issue(Long userId, Coupon coupon) {
        MemberCoupon memberCoupon = new MemberCoupon();
        memberCoupon.userId = userId;
        memberCoupon.coupon = coupon;
        memberCoupon.couponStatus = CouponStatus.AVAILABLE;
        memberCoupon.expiredAt = coupon.getEndDate().plusDays(30);
        return memberCoupon;
    }

    public boolean isUsuable() {
        if (this.couponStatus != CouponStatus.AVAILABLE) {
            return false;
        }
        if (isExpired()) {
            return false;
        }
        return true;
    }

    public void validateUsable() {
        if (this.couponStatus != CouponStatus.AVAILABLE) {
            throw new BusinessException(
                    ErrorCode.COUPON_APPLIED_FALIED,
                    "이미 사용되었거나 만료된 쿠폰입니다"
            );
        }
        if (isExpired()) {
            throw new BusinessException(
                    ErrorCode.COUPON_EXPIRED,
                    "쿠폰 유효기간이 만료되었습니다"
            );
        }
    }

    public void use(Long orderId) {
        validateUsable();
        this.couponStatus = CouponStatus.USED;
        this.usedOrderId = orderId;
        this.usedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return this.expiredAt != null && LocalDateTime.now().isAfter(this.expiredAt);
    }

    public void rollbackUsage() {
        if (this.couponStatus != CouponStatus.USED) {
            throw new BusinessException(
                    ErrorCode.COUPON_NOT_USED,
                    "사용되지 않은 쿠폰은 롤백할 수 없습니다"
            );
        }
        this.couponStatus = CouponStatus.AVAILABLE;
        this.usedAt = null;
        this.usedOrderId = null;
    }
}