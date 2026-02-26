package com.mudosa.musinsa.chat.broker.config.rabbitmq;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
  @Value("${spring.rabbitmq.host:localhost}")
  private String rabbitHost;

  @Bean
  public TopicExchange chatExchange() {
    return new TopicExchange("chat", true, false);
  }

  @Bean
  public CachingConnectionFactory connectionFactory() {
    CachingConnectionFactory factory = new CachingConnectionFactory();
    factory.setHost(rabbitHost);
    factory.setPort(5672);
    factory.setUsername("guest");
    factory.setPassword("guest");

    // Publisher Confirm
    factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

    return factory;
  }

  @Bean
  public MessageConverter jacksonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory,
                                       MessageConverter messageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);   // JSON 직렬화
    template.setExchange("chat");                     // 필요하면 기본 exchange 지정
    return template;
  }

  @Bean
  public Queue defaultQueue() {
    return new Queue("chat.default", true);
  }

  @Bean
  public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(CachingConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(10);
    return factory;
  }
}