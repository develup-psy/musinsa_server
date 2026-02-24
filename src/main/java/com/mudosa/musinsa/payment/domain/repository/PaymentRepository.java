package com.mudosa.musinsa.payment.domain.repository;

import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
