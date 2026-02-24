package com.mudosa.musinsa.coupon.repository;

import com.mudosa.musinsa.coupon.model.MemberCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long>, MemberCouponRepositoryCustom {
    Optional<MemberCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
    long countByUserIdAndCouponId(Long userId,Long couponId);
    // 쿠폰별 전체 발급 개수 조회
    long countByCouponId(Long couponId);
    List<MemberCoupon> findAllByUserId(Long userId);
}
