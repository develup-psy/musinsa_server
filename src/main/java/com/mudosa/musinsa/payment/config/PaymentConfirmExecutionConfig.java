package com.mudosa.musinsa.payment.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Configuration
@EnableConfigurationProperties({
        PaymentConfirmExecutionProperties.class,
        PgCircuitBreakerProperties.class
})
public class PaymentConfirmExecutionConfig {

    @Bean(name = "paymentConfirmExecutor", destroyMethod = "close")
    public ExecutorService paymentConfirmExecutor(PaymentConfirmExecutionProperties properties) {
        if (properties.isVirtualThreadsEnabled()) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        int poolSize = Math.max(1, properties.getPlatformThreadPoolSize());
        return Executors.newFixedThreadPool(poolSize);
    }

    @Bean(name = "paymentConfirmSemaphore")
    @Qualifier("paymentConfirmSemaphore")
    public Semaphore paymentConfirmSemaphore(PaymentConfirmExecutionProperties properties) {
        PaymentConfirmExecutionProperties.Bulkhead bulkhead = properties.getBulkhead();
        int permits = Math.max(1, bulkhead.getPermits());
        return new Semaphore(permits, bulkhead.isFair());
    }
}
