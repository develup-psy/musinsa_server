package com.mudosa.musinsa.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PgCircuitBreakerProperties.class)
public class PaymentCircuitBreakerConfig {

    @Bean(name = "pgProviderCircuitBreaker")
    public CircuitBreaker pgProviderCircuitBreaker(PgCircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(properties.getSlidingWindowType())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(properties.getWaitDurationInOpenStateMillis()))
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(
                        properties.isAutomaticTransitionFromOpenToHalfOpenEnabled()
                )
                .recordException(new PgCircuitBreakerRecordFailurePredicate())
                .ignoreException(new PgCircuitBreakerIgnoreExceptionPredicate())
                .build();

        return CircuitBreaker.of("pg-provider", config);
    }
}
