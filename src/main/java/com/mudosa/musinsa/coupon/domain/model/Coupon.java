package com.mudosa.musinsa.coupon.domain.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @Column(name = "coupon_name", nullable = false, length = 100, unique = true)
    private String couponName;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    @Builder.Default
    private Integer issuedQuantity = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 쿠폰과 상품 매핑
    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CouponProduct> couponProducts = new ArrayList<>();

    public void validateAvailability(BigDecimal orderAmount) {
        // 1. 활성화 상태 검증
        if (!this.isActive) {
            throw new BusinessException(
                    ErrorCode.COUPON_APPLIED_FALIED,
                    "비활성화된 쿠폰입니다"
            );
        }

        // 2. 유효 기간 검증
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(this.startDate)) {
            throw new BusinessException(
                    ErrorCode.COUPON_EXPIRED,
                    "쿠폰 사용 시작일이 되지 않았습니다"
            );
        }

        if (now.isAfter(this.endDate)) {
            throw new BusinessException(
                    ErrorCode.COUPON_EXPIRED,
                    "쿠폰 사용 기간이 만료되었습니다"
            );
        }

        // 3. 최소 주문 금액 검증
        if (this.minOrderAmount != null &&
                orderAmount.compareTo(this.minOrderAmount) < 0) {
            throw new BusinessException(
                    ErrorCode.COUPON_APPLIED_FALIED,
                    String.format("최소 주문 금액(%s원) 미만입니다", this.minOrderAmount)
            );
        }
    }

    public BigDecimal calculateDiscountAmount(BigDecimal orderAmount) {
        BigDecimal discountAmount;

        if (this.discountType == DiscountType.AMOUNT) {
            // 정액 할인
            discountAmount = this.discountValue;

        } else if (this.discountType == DiscountType.PERCENTAGE) {
            // 정률 할인
            discountAmount = orderAmount
                    .multiply(this.discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

            // 최대 할인 금액 제한
            if (this.maxDiscountAmount != null &&
                    discountAmount.compareTo(this.maxDiscountAmount) > 0) {
                discountAmount = this.maxDiscountAmount;
            }

        } else {
            throw new BusinessException(
                    ErrorCode.INVALID_COUPON_TYPE,
                    "지원하지 않는 쿠폰 타입입니다"
            );
        }

        // 할인 금액이 주문 금액을 초과하지 않도록
        if (discountAmount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
        }

        return discountAmount;
    }

    public BigDecimal validateAndCalculateDiscount(BigDecimal orderAmount) {
        validateAvailability(orderAmount);
        return calculateDiscountAmount(orderAmount);
    }

    /*
    * 이벤트 관련 쿠폰을 위한 메서드
    */

    // 1. 활성화된 쿠폰 인지 check - 비활성화된 쿠폰이라면 여러가지 오류 메시지 return
    public void validateIssuable(LocalDateTime now) {
        if ( !Boolean.TRUE.equals(this.isActive) ) {
            throw new BusinessException(ErrorCode.COUPON_APPLIED_FALIED, "비활성화된 쿠폰입니다.");
        }

        if(now.isBefore(this.startDate) || now.isAfter(this.endDate)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED, "쿠폰 발급 가능 기간이 아닙니다");
        }

        if(this.totalQuantity != null && this.issuedQuantity >= this.totalQuantity) {
            throw new BusinessException(ErrorCode.COUPON_OUT_OF_STOCK, "쿠폰 재고가 모두 소진되었습니다");
        }

    }

    // 2. 쿠폰 발급 관련 메서드 - 실제 신규 발급으로 확정되었을 때 발급 수량 증가 (재고 차감 효과)
    // 주의: 재고 검증은 validateIssuable()에서 먼저 수행되어야 함

    public void increaseIssuedQuantity() {
        this.issuedQuantity = (this.issuedQuantity == null ? 0 : this.issuedQuantity) + 1;
    }

    // 남은 재고 조회 (totalQuantity - issuedQuantity)
    public Integer getRemainingQuantity() {
        if (this.totalQuantity == null) {
            return null; // 무제한 발급
        }
        int issued = (this.issuedQuantity == null ? 0 : this.issuedQuantity);
        return Math.max(0, this.totalQuantity - issued);
    }





}
