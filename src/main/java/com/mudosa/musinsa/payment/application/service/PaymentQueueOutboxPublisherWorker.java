package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.application.dto.queue.PaymentQueueMessage;
import com.mudosa.musinsa.payment.domain.model.PaymentQueueOutboxEvent;
import com.mudosa.musinsa.payment.domain.repository.PaymentQueueOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.queue.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentQueueOutboxPublisherWorker {

    private final PaymentQueueOutboxEventRepository paymentQueueOutboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.queue.kafka.topic:payment.confirm.queue}")
    private String queueTopic;

    @Value("${payment.queue.outbox.batch-size:200}")
    private int batchSize;

    @Value("${payment.queue.outbox.retry.max-retries:5}")
    private int maxRetries;

    @Value("${payment.queue.outbox.retry.initial-delay-millis:500}")
    private long retryInitialDelayMillis;

    @Value("${payment.queue.outbox.retry.max-delay-millis:30000}")
    private long retryMaxDelayMillis;

    @Scheduled(fixedDelayString = "${payment.queue.outbox.publisher-delay-millis:200}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> eventIds = paymentQueueOutboxEventRepository.findReadyEventIds(batchSize, now);
        if (eventIds.isEmpty()) {
            return;
        }

        for (Long eventId : eventIds) {
            try {
                publishOne(eventId, now);
            } catch (Exception e) {
                log.error("[PaymentQueueOutboxPublisher] outbox 발행 예외: eventId={}", eventId, e);
            }
        }
    }

    private void publishOne(Long eventId, LocalDateTime now) {
        PaymentQueueOutboxEvent event = paymentQueueOutboxEventRepository.findByIdForUpdate(eventId)
                .orElse(null);

        if (event == null || !event.isReadyToPublish(now)) {
            return;
        }

        PaymentQueueMessage message = PaymentQueueMessage.builder()
                .outboxEventId(event.getPaymentQueueOutboxEventId())
                .paymentId(event.getPaymentId())
                .orderNo(event.getOrderNo())
                .build();

        try {
            kafkaTemplate.send(queueTopic, String.valueOf(event.getPaymentId()), message)
                    .get(3, TimeUnit.SECONDS);
            event.markPublished();
        } catch (Exception e) {
            handleRetryOrFail(event, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handleRetryOrFail(PaymentQueueOutboxEvent event, String reason) {
        int nextRetryCount = event.getRetryCount() + 1;
        if (nextRetryCount > maxRetries) {
            event.markFailed(reason + ", maxRetriesExceeded=" + maxRetries);
            log.error("[PaymentQueueOutboxPublisher] 최종 실패: eventId={}, retryCount={}, maxRetries={}",
                    event.getPaymentQueueOutboxEventId(), event.getRetryCount(), maxRetries);
            return;
        }

        long delayMillis = computeBackoffDelayMillis(nextRetryCount);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(delayMillis * 1_000_000);
        event.markRetryScheduled(reason, nextRetryAt);
        log.warn("[PaymentQueueOutboxPublisher] 재시도 예약: eventId={}, retryCount={}, nextRetryAt={}",
                event.getPaymentQueueOutboxEventId(), event.getRetryCount(), nextRetryAt);
    }

    private long computeBackoffDelayMillis(int retryCount) {
        int exponent = Math.min(Math.max(0, retryCount - 1), 30);
        long delay = retryInitialDelayMillis * (1L << exponent);
        return Math.min(delay, retryMaxDelayMillis);
    }
}
