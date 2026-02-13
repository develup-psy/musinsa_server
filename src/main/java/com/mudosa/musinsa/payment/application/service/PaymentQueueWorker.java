package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueueWorker {

    private final PaymentQueueService paymentQueueService;
    private final PaymentProcessor paymentProcessor;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentRepository paymentRepository;

    /**
     * 매 1초마다 대기열에서 최대 batchSize건을 꺼내 PG 승인 처리
     * ZPOPMIN은 꺼내기와 삭제가 원자적으로 수행되어 경합 조건 방지
     */
    @Scheduled(fixedRate = 1000)
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<Object>> batch = paymentQueueService.popBatch();

        if (batch == null || batch.isEmpty()) {
            return;
        }

        log.info("[PaymentQueueWorker] 배치 처리 시작: {}건", batch.size());

        // 병렬 처리 (Virtual Thread 활용 시 더 효율적)
        batch.parallelStream().forEach(tuple -> {
            Long paymentId = parsePaymentId(tuple);
            if (paymentId != null) {
                processPayment(paymentId);
            }
        });

        log.info("[PaymentQueueWorker] 배치 처리 완료: {}건", batch.size());
    }

    /**
     * 개별 결제 처리
     * 1. DB에서 결제 정보 조회
     * 2. 결제 상태를 PENDING으로 전환 (PG 호출 중)
     * 3. PG 승인 요청
     * 4. 성공: approvePayment / 실패: failPayment (보상 트랜잭션)
     */
    private void processPayment(Long paymentId) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElse(null);

            if (payment == null) {
                log.warn("[PaymentQueueWorker] 결제 정보 없음: paymentId={}", paymentId);
                return;
            }

            // QUEUED가 아닌 상태면 스킵 (중복 처리 방지)
            if (payment.getStatus() != com.mudosa.musinsa.payment.domain.model.PaymentStatus.QUEUED) {
                log.warn("[PaymentQueueWorker] QUEUED 상태가 아님: paymentId={}, status={}", paymentId, payment.getStatus());
                return;
            }

            // PG 승인 요청을 위한 Request 조립
            PaymentConfirmRequest pgRequest = PaymentConfirmRequest.builder()
                    .paymentKey(payment.getPgTransactionId())
                    .orderNo(String.valueOf(payment.getOrderId()))
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
            handlePaymentFailure(paymentId, e.getMessage());
        } catch (Exception e) {
            log.error("[PaymentQueueWorker] 결제 처리 중 예상치 못한 오류: paymentId={}", paymentId, e);
            handlePaymentFailure(paymentId, "시스템 오류로 결제 처리에 실패했습니다");
        }
    }

    /**
     * 결제 실패 보상 트랜잭션
     * 재고 복구 + 결제 실패 처리
     */
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
}
