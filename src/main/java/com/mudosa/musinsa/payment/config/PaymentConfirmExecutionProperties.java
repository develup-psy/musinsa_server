package com.mudosa.musinsa.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment.confirm-execution")
public class PaymentConfirmExecutionProperties {

    /**
     * confirm API에 비동기 실행기 적용 여부.
     */
    private boolean enabled = true;

    /**
     * 실행기를 가상 스레드로 구성할지 여부.
     */
    private boolean virtualThreadsEnabled = true;

    /**
     * 가상 스레드를 쓰지 않을 때 사용할 플랫폼 스레드 수.
     */
    private int platformThreadPoolSize = 32;

    private final Bulkhead bulkhead = new Bulkhead();

    @Getter
    @Setter
    public static class Bulkhead {

        /**
         * 동시 처리량 제한 적용 여부.
         */
        private boolean enabled = true;

        /**
         * 동시에 처리 가능한 결제 confirm 요청 수.
         */
        private int permits = 50;

        /**
         * permit 획득 대기 시간(ms). 초과 시 빠르게 실패한다.
         */
        private long acquireTimeoutMillis = 50L;

        /**
         * 공정 세마포어 사용 여부.
         */
        private boolean fair = true;
    }
}
