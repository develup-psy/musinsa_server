package com.mudosa.musinsa.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment.pg-circuit-breaker")
public class PgCircuitBreakerProperties {

    private boolean enabled = true;

    private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

    private int slidingWindowSize = 20;

    private int minimumNumberOfCalls = 10;

    private float failureRateThreshold = 50.0f;

    private long waitDurationInOpenStateMillis = 10000L;

    private int permittedNumberOfCallsInHalfOpenState = 3;

    private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
}
