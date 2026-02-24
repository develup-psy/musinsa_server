package com.mudosa.musinsa.chat.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisBrokerAdapter implements ChatMessageBroker {

  // pub/sub 전용 템플릿 (String JSON)
  private final RedisTemplate<String, String> redisPubSubTemplate;
  private final ObjectMapper redisObjectMapper;

  @Override
  public void sendToTopic(String destination, Object payload) {
    publish(destination, payload);
  }

  @Override
  public void sendToUser(String sessionId, String destination, Object payload) {
    // 필요하면 이런 규칙으로 채널 분리
    String channel = destination + ":" + sessionId;
    publish(channel, payload);
  }

  @Override
  public void broadcast(String destination, Object payload) {
    publish(destination, payload);
  }

  private void publish(String channel, Object payload) {
    try {
      // DTO -> JSON 문자열
      String json = redisObjectMapper.writeValueAsString(payload);
      log.debug("[RedisBroker] publish -> channel={}, payload={}", channel, json);

      // JSON String 그대로 pub/sub
      redisPubSubTemplate.convertAndSend(channel, json);
    } catch (Exception e) {
      log.error("[RedisBroker] publish failed. channel={}", channel, e);
      throw new IllegalStateException("Redis publish serialization failed", e);
    }
  }
}
