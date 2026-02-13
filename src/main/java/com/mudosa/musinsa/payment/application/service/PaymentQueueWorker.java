package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment.queue.worker.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentQueueWorker {

    private final PaymentQueueService paymentQueueService;
    private final PaymentProcessor paymentProcessor;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentRepository paymentRepository;

    @Scheduled(fixedDelayString = "${pg.queue.worker-delay-millis:200}")
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<Object>> batch;
        try {
            batch = paymentQueueService.popBatch();
        } catch (Exception e) {
            log.warn("[PaymentQueueWorker] 큐 조회 실패", e);
            return;
        }

        if (batch == null || batch.isEmpty()) {
            return;
        }

        log.info("[PaymentQueueWorker] 배치 처리 시작: {}건", batch.size());

        // 처리량 우선 전략: 배치 내 결제는 병렬 처리한다.
        batch.parallelStream().forEach(tuple -> {
            Long paymentId = parsePaymentId(tuple);
            if (paymentId != null) {
                processPayment(paymentId, tuple.getScore());
            }
        });

        log.info("[PaymentQueueWorker] 배치 처리 완료: {}건", batch.size());
    }

    private void processPayment(Long paymentId, Double originalScore) {
        boolean processingStarted = false;
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElse(null);

            if (payment == null) {
                log.warn("[PaymentQueueWorker] 결제 정보 없음: paymentId={}", paymentId);
                return;
            }

            // QUEUED가 아닌 상태면 스킵
            if (!payment.isQueue()) {
                log.warn("[PaymentQueueWorker] QUEUED 상태가 아님: paymentId={}, status={}", paymentId, payment.getStatus());
                return;
            }

            // 외부 API 제한에 맞추기
            if (!paymentQueueService.tryAcquireRateSlot(paymentId)) {
                safeRequeue(paymentId, originalScore);
                return;
            }

            // 상태 전이
            processingStarted = paymentConfirmService.startProcessingIfQueued(paymentId, payment.getUserId());
            if (!processingStarted) return;

            // PG 승인 요청을 위한 Request 조립
            PaymentConfirmRequest pgRequest = PaymentConfirmRequest.builder()
                    .paymentKey(payment.getPgTransactionId())
                    .orderNo(payment.getOrderNo())
                    .amount(payment.getAmount().longValue())
                    .build();

            // PG 호출
            PaymentResponseDto pgResponse = paymentProcessor.processPayment(pgRequest);

            // TX2: 결제 승인
            paymentConfirmService.approvePayment(
                    paymentId,
                    payment.getUserId(),
                    pgResponse,
                    payment.getOrderId()
            );

            log.info("[PaymentQueueWorker] 결제 승인 완료: paymentId={}", paymentId);

        } catch (BusinessException e) {
            log.error("[PaymentQueueWorker] 결제 처리 실패: paymentId={}, error={}", paymentId, e.getMessage());
            if (processingStarted) {
                handlePaymentFailure(paymentId, e.getMessage());
            } else {
                safeRequeue(paymentId, originalScore);
            }
        } catch (Exception e) {
            log.error("[PaymentQueueWorker] 결제 처리 중 예상치 못한 오류: paymentId={}", paymentId, e);
            if (processingStarted) {
                handlePaymentFailure(paymentId, "시스템 오류로 결제 처리에 실패했습니다");
            } else {
                safeRequeue(paymentId, originalScore);
            }
        }
    }

    private void handlePaymentFailure(Long paymentId, String errorMessage) {
        try {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment != null) {
                paymentConfirmService.failPayment(
                        paymentId,
                        errorMessage,
                        payment.getUserId(),
                        payment.getOrderId()
                );
            }
        } catch (Exception e) {
            log.error("[PaymentQueueWorker] 보상 트랜잭션 실패: paymentId={}", paymentId, e);
        }
    }

    private Long parsePaymentId(ZSetOperations.TypedTuple<Object> tuple) {
        try {
            Object value = tuple.getValue();
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString().replaceAll("\"", ""));
        } catch (Exception e) {
            log.error("[PaymentQueueWorker] paymentId 파싱 실패: value={}", tuple.getValue(), e);
            return null;
        }
    }

    private void safeRequeue(Long paymentId, Double originalScore) {
        try {
            paymentQueueService.requeue(paymentId, originalScore);
        } catch (Exception e) {
            // Redis가 내려가도 DB는 QUEUED 상태이므로 Reconciler가 복구한다.
            log.warn("[PaymentQueueWorker] 재큐잉 실패(복구 대기): paymentId={}", paymentId, e);
        }
    }
}
