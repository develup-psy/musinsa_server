package com.mudosa.musinsa.coupon.repository;

import com.mudosa.musinsa.coupon.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /*
     * 비관적 쓰기 락을 사용한 쿠폰 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @QueryHints({
//            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
//    })

    //* - QueryHint 제거: DB 기본 락 타임아웃 사용 (보통 수 초 이내)
    @Query("SELECT c FROM Coupon c WHERE c.id = :couponId")
    Optional<Coupon> findByIdForUpdate(@Param("couponId") Long couponId);

    /**
     * 일반 조회 (락 없음)
     * - 읽기 전용 작업에서 사용
     * - couponProducts가 필요한 경우 fetch join 사용
     */
    @Query("SELECT DISTINCT c FROM Coupon c " +
            "LEFT JOIN FETCH c.couponProducts cp " +
            "WHERE c.id = :couponId")
    Optional<Coupon> findByIdWithProducts(@Param("couponId") Long couponId);
}