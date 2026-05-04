package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.common.client.RestTemplateClient;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.exception.ExternalApiException;
import com.mudosa.musinsa.payment.application.dto.request.TossPaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.TossPaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.TossPaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.TossPaymentConfirmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class TossPaymentService {

    @Value("${tosspayments.confirm_url}")
    private String tossPaymentsConfirmUrl;

    @Value("${tosspayments.base_cancel_url}")
    private String tossPaymentsBaseCancelUrl;

    @Value("${tosspayments.secret-key}")
    private String tossPaymentsSecretKey;

    private final RestTemplateClient restTemplateClient;

    @Retryable(
            retryFor = ExternalApiException.class,
            noRetryFor = BusinessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    public TossPaymentConfirmResponse callTossApi(TossPaymentConfirmRequest request) {
        // 헤더 생성
        HttpHeaders headers = createBasicAuthHeaders(tossPaymentsSecretKey);

        // HttpEntity 생성
        HttpEntity<TossPaymentConfirmRequest> entity = new HttpEntity<>(request, headers);

        try {
            // API 호출
            return restTemplateClient.executePayment(
                    tossPaymentsConfirmUrl,
                    entity,
                    TossPaymentConfirmResponse.class,
                    request.getOrderId()
            );

        } catch (ExternalApiException e) {
            if (e.getHttpStatus() == HttpStatus.REQUEST_TIMEOUT) {
                log.error("[Toss] 타임아웃 발생 - orderNo: {}", request.getOrderId());
                throw new BusinessException(
                        ErrorCode.PAYMENT_TIMEOUT,
                        "결제 처리 시간이 초과되었습니다"
                );
            }
            throw e;
        }
    }

    @Retryable(
            retryFor = ExternalApiException.class,
            noRetryFor = BusinessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    public TossPaymentCancelResponse callTossCancelApi(TossPaymentCancelRequest request) {
        HttpHeaders headers = createBasicAuthHeaders(tossPaymentsSecretKey);
        HttpEntity<TossPaymentCancelRequest> entity = new HttpEntity<>(request, headers);

        try {
            String fullCancelUrl = UriComponentsBuilder.fromHttpUrl(tossPaymentsBaseCancelUrl)
                    .path("/{paymentKey}/cancel")
                    .buildAndExpand(request.getPaymentKey())
                    .toUriString();

            return restTemplateClient.executePayment(
                    fullCancelUrl,
                    entity,
                    TossPaymentCancelResponse.class,
                    request.getPaymentKey()
            );
        } catch (ExternalApiException e) {
            if (e.getHttpStatus() == HttpStatus.REQUEST_TIMEOUT) {
                log.error("[Toss] 취소 타임아웃 - paymentKey: {}", request.getPaymentKey());
                throw new BusinessException(
                        ErrorCode.PAYMENT_CANCEL_TIMEOUT,
                        "결제 취소 처리 시간이 초과되었습니다"
                );
            }
            throw e;
        }
    }


    @Recover
    public TossPaymentConfirmResponse recover(
            ExternalApiException e,
            TossPaymentConfirmRequest request
    ) {
        log.error("[Toss] 모든 재시도 실패 - orderNo: {}, error: {}",
                request.getOrderId(),
                e.getMessage());
        throw mapConfirmFailure(e);
    }

    @Recover
    public TossPaymentConfirmResponse recover(
            BusinessException e,
            TossPaymentConfirmRequest request
    ) {
        log.error("[Toss] 응답 검증 실패 - orderNo: {}, error: {}",
                request.getOrderId(),
                e.getMessage());
        throw e;
    }

    @Recover
    public TossPaymentCancelResponse recoverCancel(
            ExternalApiException e,
            TossPaymentCancelRequest request
    ) {
        log.error("[Toss] 취소 재시도 실패 - paymentKey: {}", request.getPaymentKey());
        throw mapCancelFailure(e);
    }

    // 헤더 생성 메서드
    private HttpHeaders createBasicAuthHeaders(String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String auth = secretKey + ":";
        String encoded = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

        return headers;
    }

    private BusinessException mapConfirmFailure(ExternalApiException e) {
        HttpStatusCode statusCode = e.getHttpStatus();
        if (statusCode != null) {
            if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return new BusinessException(ErrorCode.PG_RATE_LIMIT_EXCEEDED, e.getMessage());
            }
            if (statusCode.is4xxClientError()) {
                return new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, e.getMessage());
            }
        }
        return new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, e.getMessage());
    }

    private BusinessException mapCancelFailure(ExternalApiException e) {
        HttpStatusCode statusCode = e.getHttpStatus();
        if (statusCode != null) {
            if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return new BusinessException(ErrorCode.PG_RATE_LIMIT_EXCEEDED, e.getMessage());
            }
            if (statusCode.is4xxClientError()) {
                return new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, e.getMessage());
            }
        }
        return new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, e.getMessage());
    }

}
