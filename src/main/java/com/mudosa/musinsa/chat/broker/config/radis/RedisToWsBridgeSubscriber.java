package com.mudosa.musinsa.chat.broker.config.radis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudosa.musinsa.chat.dto.WSFileUploadSuccessDTO;
import com.mudosa.musinsa.chat.dto.WSMessageResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToWsBridgeSubscriber {

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper redisObjectMapper;

  /**
   * RedisMessageListenerAdapter 에서 (String message, String channel) 시그니처로 호출됨
   */
  public void handleMessage(String message, String channel) throws Exception {
    try {
      JsonNode root = redisObjectMapper.readTree(message);
      String type = root.path("type").asText("");
      long chatId = root.path("chatId").asLong(0L);

      if (chatId <= 0) {
        log.warn("[RedisBridge] chatId missing. channel={}, raw={}", channel, message);
        return;
      }

      String destination = "/topic/chat/" + chatId;
      log.debug("[RedisBridge] recv channel={}, type={}, dest={}", channel, type, destination);

      switch (type) {
        case "MESSAGE" -> {
          WSMessageResponseDTO dto =
              redisObjectMapper.treeToValue(root, WSMessageResponseDTO.class);
          log.debug("[RedisBridge] fan-out MESSAGE -> dest={}, msgId={}", destination, dto.getMessageId());
          messagingTemplate.convertAndSend(destination, dto);
        }
        case "ATTACHMENT" -> {
          WSFileUploadSuccessDTO dto =
              redisObjectMapper.treeToValue(root, WSFileUploadSuccessDTO.class);
          log.debug("[RedisBridge] fan-out ATTACHMENT -> dest={}, msgId={}", destination, dto.getMessageId());
          messagingTemplate.convertAndSend(destination, dto);
        }
        default -> {
          log.warn("[RedisBridge] Unknown payload type. channel={}, type={}, raw={}",
              channel, type, message);
        }
      }

    } catch (Exception e) {
      log.error("[RedisBridge] handleMessage failed. channel={}, raw={}", channel, message, e);
      throw e;
    }
  }
}
