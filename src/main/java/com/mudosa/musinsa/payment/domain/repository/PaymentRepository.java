package com.mudosa.musinsa.payment.domain.repository;

import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Payment findByPgTransactionId(String paymentTransactionId);

    List<Payment> findTop200ByStatusOrderByCreatedAtAsc(PaymentStatus status);

    List<Payment> findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(PaymentStatus status, LocalDateTime threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment p
               set p.status = :toStatus,
                   p.updatedAt = :now
             where p.id = :paymentId
               and p.status = :fromStatus
            """)
    int updateStatusIfCurrent(
            @Param("paymentId") Long paymentId,
            @Param("fromStatus") PaymentStatus fromStatus,
            @Param("toStatus") PaymentStatus toStatus,
            @Param("now") LocalDateTime now
    );
}
