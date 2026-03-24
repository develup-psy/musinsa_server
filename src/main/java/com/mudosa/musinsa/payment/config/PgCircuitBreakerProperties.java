package com.mudosa.musinsa.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment.pg-circuit-breaker")
public class PgCircuitBreakerProperties {

    private boolean enabled = true;

    /**
     * CLOSED 상태에서 연속 실패 임계치.
     */
    private int failureThreshold = 5;

    /**
     * OPEN 상태 유지 시간(ms).
     */
    private long openStateDurationMillis = 10000L;

    /**
     * HALF_OPEN 상태에서 허용할 최대 탐색 호출 수.
     */
    private int halfOpenMaxCalls = 3;

    /**
     * HALF_OPEN 상태에서 CLOSED 복귀를 위한 성공 횟수.
     */
    private int halfOpenSuccessThreshold = 2;
}
