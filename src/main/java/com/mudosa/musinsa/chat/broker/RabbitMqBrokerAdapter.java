package com.mudosa.musinsa.chat.broker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RabbitMqBrokerAdapter implements ChatMessageBroker {

  private final SimpMessageSendingOperations messagingTemplate;

  public RabbitMqBrokerAdapter(SimpMessageSendingOperations messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void sendToTopic(String destination, Object payload) {
    messagingTemplate.convertAndSend(destination, payload);
  }

  @Override
  public void sendToUser(String sessionId, String destination, Object payload) {
    // 필요 시 userName 기반으로 사용
    messagingTemplate.convertAndSendToUser(sessionId, destination, payload);
  }

  @Override
  public void broadcast(String destination, Object payload) {
    messagingTemplate.convertAndSend(destination, payload);
  }
}
