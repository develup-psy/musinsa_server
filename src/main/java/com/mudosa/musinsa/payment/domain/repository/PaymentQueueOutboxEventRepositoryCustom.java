package com.mudosa.musinsa.payment.domain.repository;

import com.mudosa.musinsa.payment.domain.model.PaymentQueueOutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentQueueOutboxEventRepositoryCustom {

    List<Long> findReadyEventIds(int limit, LocalDateTime now);

    Optional<PaymentQueueOutboxEvent> findByIdForUpdate(Long eventId);

    Optional<PaymentQueueOutboxEvent> findByPaymentIdForUpdate(Long paymentId);
}
