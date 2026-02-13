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
    private final PaymentQueueService paymentQueueService;
    private final PaymentConfirmService paymentConfirmService;

    @Value("${pg.queue.processing-timeout-seconds:30}")
    private long processingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${pg.queue.reconcile-delay-millis:5000}")
    public void reconcileQueuedPayments() {
        List<Payment> queuedPayments = paymentRepository.findTop200ByStatusOrderByCreatedAtAsc(PaymentStatus.QUEUED);
        if (queuedPayments.isEmpty()) {
            return;
        }

        for (Payment payment : queuedPayments) {
            try {
                paymentQueueService.enqueue(payment.getId(), payment.getCreatedAt());
            } catch (Exception e) {
                log.warn("[PaymentQueueRecovery] 재적재 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${pg.queue.sweeper-delay-millis:5000}")
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
                    paymentQueueService.enqueue(payment.getId(), payment.getCreatedAt());
                }
            } catch (Exception e) {
                log.error("[PaymentQueueRecovery] 타임아웃 복구 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

}
