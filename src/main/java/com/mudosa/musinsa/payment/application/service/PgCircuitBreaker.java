package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.payment.config.PgCircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.function.Supplier;

@Slf4j
@Component
public class PgCircuitBreaker {

    private final CircuitBreaker circuitBreaker;
    private final PgCircuitBreakerProperties properties;

    public PgCircuitBreaker(
            @Qualifier("pgProviderCircuitBreaker") CircuitBreaker circuitBreaker,
            PgCircuitBreakerProperties properties
    ) {
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        registerEventLogging();
    }

    public <T> T execute(String operationName, Supplier<T> task) {
        if (!properties.isEnabled()) {
            return task.get();
        }

        try {
            return circuitBreaker.executeSupplier(task);
        } catch (CallNotPermittedException ex) {
            log.warn("[PgCircuitBreaker] callNotPermitted: operation={}, state={}",
                    operationName, circuitBreaker.getState());
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                    "결제사 응답이 불안정하여 잠시 후 재시도가 필요합니다."
            );
        }
    }

    public String getState() {
        return circuitBreaker.getState().name();
    }

    private void registerEventLogging() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[PgCircuitBreaker] stateTransition: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ))
                .onFailureRateExceeded(event -> log.warn(
                        "[PgCircuitBreaker] failureRateExceeded: circuit={}, failureRate={}",
                        event.getCircuitBreakerName(),
                        event.getFailureRate()
                ));
    }
}
