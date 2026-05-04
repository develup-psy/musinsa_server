package com.mudosa.musinsa.payment.config;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.exception.ExternalApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.function.Predicate;

public class PgCircuitBreakerRecordFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof ExternalApiException externalApiException) {
            HttpStatusCode statusCode = externalApiException.getHttpStatus();
            if (statusCode == null) {
                return true;
            }
            if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return false;
            }
            return statusCode.is5xxServerError() || statusCode.value() == HttpStatus.REQUEST_TIMEOUT.value();
        }

        if (throwable instanceof ResourceAccessException || throwable instanceof RestClientException) {
            return true;
        }

        if (throwable instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.getErrorCode();
            return errorCode == ErrorCode.PAYMENT_TIMEOUT
                    || errorCode == ErrorCode.PAYMENT_CANCEL_TIMEOUT
                    || errorCode == ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
        }

        return false;
    }
}
