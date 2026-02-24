package com.mudosa.musinsa.chat.broker.config;

import com.mudosa.musinsa.chat.broker.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrokerConfig {

  @Value("${app.broker.type:rabbit}")
  private String brokerType;

  @Bean
  public ChatMessageBroker chatMessageBroker(
      SimpleMessageBrokerAdapter simple,
      RedisBrokerAdapter redis,
      RabbitMqBrokerAdapter rabbit,
      KafkaBrokerAdapter kafka
  ) {
    return switch (brokerType) {
      case "redis" -> redis;
      case "rabbit" -> rabbit;
      case "kafka" -> kafka;
      default -> simple;
    };
  }
}
