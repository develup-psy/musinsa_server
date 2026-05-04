package com.mudosa.musinsa.payment.domain.repository;

import com.mudosa.musinsa.payment.domain.model.PaymentQueueOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentQueueOutboxEventRepository
        extends JpaRepository<PaymentQueueOutboxEvent, Long>, PaymentQueueOutboxEventRepositoryCustom {

    Optional<PaymentQueueOutboxEvent> findByPaymentId(Long paymentId);
}
