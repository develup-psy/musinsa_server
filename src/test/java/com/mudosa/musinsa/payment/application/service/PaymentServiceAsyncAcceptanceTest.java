package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.payment.application.dto.PaymentCreationResult;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.PaymentQueueResponse;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceAsyncAcceptanceTest {

    @Mock
    private PaymentConfirmService paymentConfirmService;

    @InjectMocks
    private PaymentService paymentService;

    @DisplayName("결제 접수 시 ACCEPTED 응답을 반환한다")
    @Test
    void confirmPaymentAsync_AcceptsRequest() {
        Long userId = 1L;
        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo("ORD-ASYNC-001")
                .paymentKey("payment_key_123")
                .amount(12000L)
                .pgProvider(PgProvider.TOSS)
                .build();

        PaymentCreationResult creationResult = PaymentCreationResult.builder()
                .paymentId(10L)
                .orderId(20L)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentConfirmService.createQueuedPayment(any(), any(), any()))
                .thenReturn(creationResult);

        PaymentQueueResponse response = paymentService.confirmPaymentAsync(request, userId);

        assertThat(response.getTicketId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getMessage()).isEqualTo("결제 요청이 접수되었습니다.");

        verify(paymentConfirmService, never()).failPayment(any(), any(), any(), any());
    }
}
