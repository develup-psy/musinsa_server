package com.mudosa.musinsa.coupon.repository;

import com.mudosa.musinsa.coupon.model.CouponProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponProductRepository extends JpaRepository<CouponProduct, Long> {

    boolean existsByCouponIdAndProductId(Long couponId, Long productId);

}
