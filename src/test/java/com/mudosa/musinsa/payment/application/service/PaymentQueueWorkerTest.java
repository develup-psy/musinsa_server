package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueueWorkerTest {

    @Mock
    private PaymentQueueService paymentQueueService;
    @Mock
    private PaymentProcessor paymentProcessor;
    @Mock
    private PaymentConfirmService paymentConfirmService;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentQueueWorker paymentQueueWorker;

    @DisplayName("Rate slot 획득 실패 시 PG 호출 없이 재큐잉한다")
    @Test
    void processQueue_RequeueWhenRateSlotDenied() {
        Set<ZSetOperations.TypedTuple<String>> batch = Set.of(
                new DefaultTypedTuple<>("1", 1000d)
        );

        Payment payment = Payment.createQueued(
                10L,
                "ORD-QUEUE-001",
                BigDecimal.valueOf(15000),
                PgProvider.TOSS,
                "payment_key",
                1L
        );

        when(paymentQueueService.popBatch()).thenReturn(batch);
        when(paymentRepository.findById(1L)).thenReturn(java.util.Optional.of(payment));
        when(paymentQueueService.tryAcquireRateSlot(1L)).thenReturn(false);

        paymentQueueWorker.processQueue();

        verify(paymentQueueService).requeue(eq(1L), anyDouble());
        verify(paymentProcessor, never()).processPayment(any());
        verify(paymentConfirmService, never()).approvePayment(any(), any(), any(), any());
    }

    @DisplayName("Rate slot 획득 및 처리 시작 성공 시 PG 승인 후 APPROVED 처리한다")
    @Test
    void processQueue_ApproveWhenRateSlotAllowed() {
        Set<ZSetOperations.TypedTuple<String>> batch = Set.of(
                new DefaultTypedTuple<>("2", 2000d)
        );

        Payment payment = Payment.createQueued(
                20L,
                "ORD-QUEUE-002",
                BigDecimal.valueOf(22000),
                PgProvider.TOSS,
                "payment_key_2",
                3L
        );

        PaymentResponseDto pgResponse = PaymentResponseDto.builder()
                .paymentKey("pg_tx_123")
                .orderNo("ORD-QUEUE-002")
                .approvedAt(LocalDateTime.now())
                .method("카드")
                .build();

        when(paymentQueueService.popBatch()).thenReturn(batch);
        when(paymentRepository.findById(2L)).thenReturn(java.util.Optional.of(payment));
        when(paymentQueueService.tryAcquireRateSlot(2L)).thenReturn(true);
        when(paymentConfirmService.startProcessingIfQueued(2L, 3L)).thenReturn(true);
        when(paymentProcessor.processPayment(any())).thenReturn(pgResponse);

        paymentQueueWorker.processQueue();

        verify(paymentConfirmService).approvePayment(eq(2L), eq(3L), eq(pgResponse), eq(20L));
    }

    @DisplayName("배치 내 결제는 순서보다 처리량을 우선해 모두 처리한다")
    @Test
    void processQueue_ProcessesAllInBatchWithParallelStrategy() {
        Set<ZSetOperations.TypedTuple<String>> batch = Set.of(
                new DefaultTypedTuple<>("2", 2000d),
                new DefaultTypedTuple<>("1", 1000d)
        );

        Payment first = Payment.createQueued(
                11L,
                "ORD-QUEUE-011",
                BigDecimal.valueOf(11000),
                PgProvider.TOSS,
                "payment_key_11",
                11L
        );

        Payment second = Payment.createQueued(
                22L,
                "ORD-QUEUE-022",
                BigDecimal.valueOf(22000),
                PgProvider.TOSS,
                "payment_key_22",
                22L
        );

        PaymentResponseDto pgResponse = PaymentResponseDto.builder()
                .paymentKey("pg_tx")
                .orderNo("ORD-QUEUE")
                .approvedAt(LocalDateTime.now())
                .method("카드")
                .build();

        when(paymentQueueService.popBatch()).thenReturn(batch);
        when(paymentRepository.findById(1L)).thenReturn(java.util.Optional.of(first));
        when(paymentRepository.findById(2L)).thenReturn(java.util.Optional.of(second));
        when(paymentQueueService.tryAcquireRateSlot(1L)).thenReturn(true);
        when(paymentQueueService.tryAcquireRateSlot(2L)).thenReturn(true);
        when(paymentConfirmService.startProcessingIfQueued(1L, 11L)).thenReturn(true);
        when(paymentConfirmService.startProcessingIfQueued(2L, 22L)).thenReturn(true);
        when(paymentProcessor.processPayment(any())).thenReturn(pgResponse);

        paymentQueueWorker.processQueue();

        verify(paymentQueueService).tryAcquireRateSlot(1L);
        verify(paymentQueueService).tryAcquireRateSlot(2L);
        verify(paymentConfirmService).startProcessingIfQueued(1L, 11L);
        verify(paymentConfirmService).startProcessingIfQueued(2L, 22L);
        verify(paymentConfirmService).approvePayment(eq(1L), eq(11L), eq(pgResponse), eq(11L));
        verify(paymentConfirmService).approvePayment(eq(2L), eq(22L), eq(pgResponse), eq(22L));
    }
}
