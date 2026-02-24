package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.OrderService;
import com.mudosa.musinsa.payment.application.dto.PaymentCreateDto;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCompensationService paymentCompensationService;

    @InjectMocks
    private PaymentConfirmService paymentConfirmService;

    @DisplayName("결제 대기열 생성 중 paymentKey 중복 시 보상 트랜잭션을 실행하고 비즈니스 예외를 반환한다")
    @Test
    void createQueuedPayment_compensatesOnDuplicatePaymentKey() {
        PaymentCreateDto request = PaymentCreateDto.builder()
                .orderNo("ORD-DUP-001")
                .totalAmount(BigDecimal.valueOf(12000))
                .pgProvider(PgProvider.TOSS)
                .build();

        when(orderService.completeOrder("ORD-DUP-001")).thenReturn(10L);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_payment_pg_transaction_id'"));

        assertThatThrownBy(() -> paymentConfirmService.createQueuedPayment(request, "DUPLICATED_KEY", 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CREATE_FAILED);
                    assertThat(e.getMessage()).contains("paymentKey");
                });

        verify(paymentCompensationService).rollbackOrderAfterQueuedCreationFailure(10L);
    }
}
