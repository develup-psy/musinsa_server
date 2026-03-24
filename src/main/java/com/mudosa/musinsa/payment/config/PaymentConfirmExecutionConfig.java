package com.mudosa.musinsa.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PgCircuitBreakerProperties.class)
public class PaymentConfirmExecutionConfig {
}
