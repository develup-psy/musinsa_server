package com.mudosa.musinsa.chat.broker.config.radis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisWsBridgeConfig {

  private final RedisConnectionFactory redisConnectionFactory;

  /**
   * 채팅 pub/sub 전용 RedisTemplate (String <-> String)
   */
  @Bean
  public RedisTemplate<String, String> redisPubSubTemplate() {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);

    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(stringSerializer);

    template.afterPropertiesSet();
    return template;
  }

  /**
   * Redis → WS 브릿지 리스너 컨테이너
   */
  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      MessageListenerAdapter chatMessageListenerAdapter
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);

    // /topic/chat/* 패턴 구독
    container.addMessageListener(chatMessageListenerAdapter, new PatternTopic("/topic/chat/*"));

    return container;
  }

  /**
   * Redis pub/sub 메시지를 RedisToWsBridgeSubscriber.handleMessage 로 위임
   */
  @Bean
  public MessageListenerAdapter chatMessageListenerAdapter(RedisToWsBridgeSubscriber subscriber) {
    MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "handleMessage");

    // pub/sub payload 를 String 으로 받기 위해 serializer 를 String 으로 고정
    adapter.setSerializer(new StringRedisSerializer());
    return adapter;
  }
}
