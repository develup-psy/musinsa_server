package com.mudosa.musinsa.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mudosa.musinsa.coupon.repository.CouponProductRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)


public class CouponProductService {

    // 쿠폰↔상품 매핑의 존재 여부로 적용 판단 가능

    private final CouponProductRepository couponProductRepository;

    public boolean isApplicableToProduct (Long couponId, Long productId) {
        if (couponId == null || productId == null) {
            return false;
        }
        return couponProductRepository.existsByCouponIdAndProductId(couponId, productId);

    }

}
