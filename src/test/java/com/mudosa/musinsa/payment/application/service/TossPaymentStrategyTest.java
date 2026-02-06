package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.common.client.RestTemplateClient;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.exception.ExternalApiException;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.TossPaymentConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Slf4j
class TossPaymentStrategyTest extends ServiceConfig {

    @Autowired
    private TossPaymentStrategy tossPaymentStrategy;

    @MockBean
    private RestTemplateClient restTemplateClient;

    @Test
    @DisplayName("결제 승인 API 호출에 성공")
    void firstAttemptSuccessNoRetry() {
        // given
        PaymentConfirmRequest request = createPaymentConfirmRequest();
        LocalDateTime now = LocalDateTime.now();
        TossPaymentConfirmResponse mockResponse = createMockTossResponse(now);

        when(restTemplateClient.executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        )).thenReturn(mockResponse);

        // when
        PaymentResponseDto result = tossPaymentStrategy.confirmPayment(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result)
                .extracting("paymentKey", "orderNo", "totalAmount")
                .contains("test_payment_key_123", "ORD_TEST_001", 50000L);

        verify(restTemplateClient, times(1)).executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        );
    }

    @DisplayName("타임아웃 발생 시 BusinessException을 던진다.")
    @Test
    void confirmPaymentTimeoutThrowsBusinessException() {
        //given
        PaymentConfirmRequest request = createPaymentConfirmRequest();

        when(restTemplateClient.executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        )).thenThrow(new ExternalApiException(
                "API 타임아웃",
                HttpStatus.REQUEST_TIMEOUT,
                "요청 시간이 초과되었습니다",
                null
        ));

        //when & then
        assertThatThrownBy(() -> tossPaymentStrategy.confirmPayment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_TIMEOUT)
                .hasMessage("결제 처리 시간이 초과되었습니다");

        verify(restTemplateClient, times(1)).executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        );
    }

    @DisplayName("재시도 로직을 모두 실패하면 예외를 발생한다")
    @Test
    void retryThreeTimesAndCallRecover() {
        //given
        PaymentConfirmRequest request = createPaymentConfirmRequest();

        // 3번 모두 실패
        when(restTemplateClient.executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        )).thenThrow(new ExternalApiException(
                "Toss API 서버 오류",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"error\":\"server_error\"}",
                null
        ));

        //when & then
        assertThatThrownBy(() -> tossPaymentStrategy.confirmPayment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Toss API 서버 오류");

        verify(restTemplateClient, times(3)).executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        );
    }

    @DisplayName("2번째 시도에서 성공하여 재시도를 한번만 한다.")
    @Test
    void retryTwoTimes() {
        //given
        PaymentConfirmRequest request = createPaymentConfirmRequest();
        LocalDateTime now = LocalDateTime.now();
        TossPaymentConfirmResponse mockResponse = createMockTossResponse(now);

        // 첫 번째는 실패, 두 번째는 성공
        when(restTemplateClient.executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        ))
                .thenThrow(new ExternalApiException(
                        "Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        null,
                        null
                ))
                .thenReturn(mockResponse);

        //when
        PaymentResponseDto result = tossPaymentStrategy.confirmPayment(request);

        //then
        assertThat(result).isNotNull();
        verify(restTemplateClient, times(2)).executePayment(
                anyString(),
                any(HttpEntity.class),
                eq(TossPaymentConfirmResponse.class),
                anyString()
        );
    }

    private PaymentConfirmRequest createPaymentConfirmRequest() {
        return PaymentConfirmRequest.builder()
                .paymentKey("test_payment_key_123")
                .orderNo("ORD_TEST_001")
                .amount(50000L)
                .build();
    }

    private TossPaymentConfirmResponse createMockTossResponse(LocalDateTime createdAt) {
        return TossPaymentConfirmResponse.builder()
                .paymentKey("test_payment_key_123")
                .orderId("ORD_TEST_001")
                .status("DONE")
                .method("카드")
                .totalAmount(50000L)
                .approvedAt(createdAt)
                .build();
    }
}