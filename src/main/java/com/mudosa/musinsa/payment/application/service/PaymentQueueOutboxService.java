package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.domain.model.PaymentQueueOutboxEvent;
import com.mudosa.musinsa.payment.domain.repository.PaymentQueueOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentQueueOutboxService {

    private final PaymentQueueOutboxEventRepository paymentQueueOutboxEventRepository;

    @Value("${payment.queue.recovery.republish-threshold-seconds:60}")
    private long republishThresholdSeconds;

    @Value("${payment.queue.recovery.reopen-failed-enabled:false}")
    private boolean reopenFailedEnabled;

    @Transactional
    public void createPendingEvent(Long paymentId, String orderNo) {
        PaymentQueueOutboxEvent event = PaymentQueueOutboxEvent.create(paymentId, orderNo);
        paymentQueueOutboxEventRepository.save(event);
    }

    @Transactional
    public void ensurePendingEvent(Long paymentId, String orderNo, String reason) {
        PaymentQueueOutboxEvent event = paymentQueueOutboxEventRepository
                .findByPaymentIdForUpdate(paymentId)
                .orElse(null);

        if (event == null) {
            paymentQueueOutboxEventRepository.save(PaymentQueueOutboxEvent.create(paymentId, orderNo));
            return;
        }

        if (event.isFailed()) {
            if (reopenFailedEnabled) {
                event.reopenPending(reason + " (failed reopen)");
            }
            return;
        }

        if (event.isPublished()) {
            LocalDateTime publishedAt = event.getPublishedAt();
            if (publishedAt == null || publishedAt.isBefore(LocalDateTime.now().minusSeconds(republishThresholdSeconds))) {
                event.reopenPending(reason + " (published timeout)");
            }
        }
    }
}
