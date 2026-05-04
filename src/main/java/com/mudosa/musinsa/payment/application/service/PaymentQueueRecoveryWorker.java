package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment.queue.worker.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentQueueRecoveryWorker {

    private final PaymentRepository paymentRepository;
    private final PaymentQueueOutboxService paymentQueueOutboxService;
    private final PaymentConfirmService paymentConfirmService;

    @Value("${payment.queue.recovery.processing-timeout-seconds:30}")
    private long processingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${payment.queue.recovery.reconcile-delay-millis:5000}")
    public void reconcileQueuedPayments() {
        List<Payment> queuedPayments = paymentRepository.findTop200ByStatusOrderByCreatedAtAsc(PaymentStatus.QUEUED);
        if (queuedPayments.isEmpty()) {
            return;
        }

        for (Payment payment : queuedPayments) {
            try {
                paymentQueueOutboxService.ensurePendingEvent(
                        payment.getId(),
                        payment.getOrderNo(),
                        "QUEUED 상태 복구 재발행"
                );
            } catch (Exception e) {
                log.warn("[PaymentQueueRecovery] outbox 복구 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.queue.recovery.sweeper-delay-millis:5000}")
    public void recoverStuckProcessing() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusSeconds(processingTimeoutSeconds);

        List<Payment> stuckPayments = paymentRepository
                .findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(PaymentStatus.PENDING, threshold);

        if (stuckPayments.isEmpty()) {
            return;
        }

        for (Payment payment : stuckPayments) {
            try {
                boolean requeued = paymentConfirmService.requeueIfPending(
                        payment.getId(),
                        "처리 타임아웃으로 재대기",
                        payment.getUserId()
                );
                if (requeued) {
                    paymentQueueOutboxService.ensurePendingEvent(
                            payment.getId(),
                            payment.getOrderNo(),
                            "PENDING 타임아웃 재발행"
                    );
                }
            } catch (Exception e) {
                log.error("[PaymentQueueRecovery] 타임아웃 복구 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

}
