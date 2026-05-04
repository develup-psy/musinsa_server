package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.queue.PaymentQueueMessage;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.queue.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentQueueConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessor paymentProcessor;
    private final PaymentConfirmService paymentConfirmService;
    private final PgRateLimiterService pgRateLimiterService;

    @Value("${payment.queue.consumer.rate-slot-wait-millis:2000}")
    private long rateSlotWaitMillis;

    @Value("${payment.queue.consumer.rate-slot-retry-delay-millis:50}")
    private long rateSlotRetryDelayMillis;

    @KafkaListener(
            topics = "${payment.queue.kafka.topic:payment.confirm.queue}",
            groupId = "${payment.queue.kafka.group-id:payment-queue-consumer}",
            concurrency = "${payment.queue.kafka.consumer-concurrency:4}",
            containerFactory = "paymentQueueKafkaListenerContainerFactory"
    )
    public void consume(PaymentQueueMessage message) {
        Long paymentId = message.getPaymentId();
        if (paymentId == null) {
            log.warn("[PaymentQueueConsumer] paymentId 없는 메시지 수신: outboxEventId={}", message.getOutboxEventId());
            return;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("[PaymentQueueConsumer] 결제 정보 없음: paymentId={}", paymentId);
            return;
        }

        if (!acquireRateSlotWithinWait(paymentId)) {
            throw new IllegalStateException("PG rate slot not available: paymentId=" + paymentId);
        }

        boolean processingStarted = paymentConfirmService.startProcessingIfQueued(paymentId, payment.getUserId());
        if (!processingStarted) {
            log.info("[PaymentQueueConsumer] 처리 시작 스킵(이미 처리됨): paymentId={}, status={}", paymentId, payment.getStatus());
            return;
        }

        try {
            PaymentConfirmRequest pgRequest = PaymentConfirmRequest.builder()
                    .paymentKey(payment.getPgTransactionId())
                    .orderNo(payment.getOrderNo())
                    .amount(payment.getAmount().longValue())
                    .build();

            PaymentResponseDto pgResponse = paymentProcessor.processPayment(pgRequest);
            paymentConfirmService.approvePayment(paymentId, payment.getUserId(), pgResponse, payment.getOrderId());
            log.info("[PaymentQueueConsumer] 결제 승인 완료: paymentId={}", paymentId);
        } catch (BusinessException e) {
            log.error("[PaymentQueueConsumer] 결제 처리 실패: paymentId={}, error={}", paymentId, e.getMessage());
            paymentConfirmService.failPayment(paymentId, e.getMessage(), payment.getUserId(), payment.getOrderId());
        } catch (Exception e) {
            log.error("[PaymentQueueConsumer] 결제 처리 중 예상치 못한 오류: paymentId={}", paymentId, e);
            paymentConfirmService.failPayment(paymentId, "시스템 오류로 결제 처리에 실패했습니다", payment.getUserId(), payment.getOrderId());
        }
    }

    private boolean acquireRateSlotWithinWait(Long paymentId) {
        long deadline = System.currentTimeMillis() + Math.max(rateSlotWaitMillis, 0);
        while (System.currentTimeMillis() <= deadline) {
            if (pgRateLimiterService.tryAcquireRateSlot("queue", String.valueOf(paymentId))) {
                return true;
            }
            try {
                Thread.sleep(Math.max(rateSlotRetryDelayMillis, 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
