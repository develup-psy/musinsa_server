package com.mudosa.musinsa.coupon.domain.repository;

import com.mudosa.musinsa.coupon.domain.model.Coupon;
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

    /**
     * 비관적 쓰기 락을 사용한 쿠폰 조회
     *
     * SELECT ... FOR UPDATE 실행
     * - InnoDB에서 해당 row에 X-Lock(배타 락) 획득
     * - 다른 트랜잭션은 이 row를 읽거나 쓸 수 없음 (대기)
     * - 트랜잭션 커밋/롤백 시 락 해제
     *
     * ✅ left join fetch 제거 이유:
     * - 쿠폰 발급 시에는 couponProducts가 필요 없음
     * - N+1 문제 방지를 위한 fetch join이지만, 여기서는 불필요
     * - 필요한 경우 별도 조회 메서드 사용
     *
     * ✅ QueryHint 설명:
     * - lock.timeout: 락 대기 시간 (밀리초)
     * - 10000ms = 10초 대기 후 LockTimeoutException 발생
     * - 기본값은 DBMS 설정을 따름 (MySQL: 50초)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
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