package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.exception.ExternalApiException;
import com.mudosa.musinsa.payment.config.PgCircuitBreakerProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgCircuitBreaker {

    private final PgCircuitBreakerProperties properties;

    @Getter
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicLong openedAtMillis = new AtomicLong(0);

    public <T> T execute(String operationName, Supplier<T> task) {
        if (!properties.isEnabled()) {
            return task.get();
        }

        ensurePermission(operationName);

        try {
            T result = task.get();
            onSuccess(operationName);
            return result;
        } catch (RuntimeException ex) {
            onFailure(operationName, ex);
            throw ex;
        }
    }

    private void ensurePermission(String operationName) {
        State current = state.get();
        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - openedAtMillis.get();
            if (elapsed >= properties.getOpenStateDurationMillis()
                    && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                halfOpenCalls.set(0);
                halfOpenSuccesses.set(0);
                log.warn("[PgCircuitBreaker] OPEN -> HALF_OPEN. operation={}", operationName);
                current = State.HALF_OPEN;
            } else {
                throw new BusinessException(
                        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                        "결제사가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요."
                );
            }
        }

        if (current == State.HALF_OPEN) {
            int allowedCalls = Math.max(1, properties.getHalfOpenMaxCalls());
            int call = halfOpenCalls.incrementAndGet();
            if (call > allowedCalls) {
                halfOpenCalls.decrementAndGet();
                throw new BusinessException(
                        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                        "결제사 상태 복구를 확인 중입니다. 잠시 후 다시 시도해주세요."
                );
            }
        }
    }

    private void onSuccess(String operationName) {
        State current = state.get();
        if (current == State.CLOSED) {
            consecutiveFailures.set(0);
            return;
        }

        if (current == State.HALF_OPEN) {
            int success = halfOpenSuccesses.incrementAndGet();
            int threshold = Math.max(1, properties.getHalfOpenSuccessThreshold());
            if (success >= threshold && state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                consecutiveFailures.set(0);
                halfOpenCalls.set(0);
                halfOpenSuccesses.set(0);
                log.info("[PgCircuitBreaker] HALF_OPEN -> CLOSED. operation={}", operationName);
            }
        }
    }

    private void onFailure(String operationName, RuntimeException ex) {
        if (!isPgFailure(ex)) {
            return;
        }

        State current = state.get();
        if (current == State.HALF_OPEN) {
            open(operationName, "half_open_probe_failed", ex);
            return;
        }

        int failures = consecutiveFailures.incrementAndGet();
        int threshold = Math.max(1, properties.getFailureThreshold());
        if (failures >= threshold) {
            open(operationName, "failure_threshold_reached", ex);
        }
    }

    private void open(String operationName, String reason, RuntimeException ex) {
        state.set(State.OPEN);
        openedAtMillis.set(System.currentTimeMillis());
        consecutiveFailures.set(0);
        halfOpenCalls.set(0);
        halfOpenSuccesses.set(0);
        log.warn("[PgCircuitBreaker] -> OPEN. operation={}, reason={}, error={}",
                operationName, reason, ex.getClass().getSimpleName());
    }

    private boolean isPgFailure(RuntimeException ex) {
        if (ex instanceof ExternalApiException) {
            return true;
        }
        if (ex instanceof ResourceAccessException || ex instanceof RestClientException) {
            return true;
        }
        if (ex instanceof BusinessException businessException) {
            ErrorCode code = businessException.getErrorCode();
            return code == ErrorCode.PAYMENT_TIMEOUT
                    || code == ErrorCode.PAYMENT_APPROVAL_FAILED
                    || code == ErrorCode.PAYMENT_CANCEL_TIMEOUT
                    || code == ErrorCode.PAYMENT_CANCEL_FAILED
                    || code == ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
        }
        return false;
    }

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
