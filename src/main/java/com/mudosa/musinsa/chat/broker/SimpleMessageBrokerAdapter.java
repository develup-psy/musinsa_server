package com.mudosa.musinsa.chat.broker;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimpleMessageBrokerAdapter implements ChatMessageBroker {
  private final SimpMessagingTemplate messagingTemplate;

  public SimpleMessageBrokerAdapter(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void sendToTopic(String destination, Object payload) {
    messagingTemplate.convertAndSend(destination, payload);
  }

  @Override
  public void sendToUser(String sessionId, String destination, Object payload) {
    messagingTemplate.convertAndSendToUser(sessionId, destination, payload);
  }

  @Override
  public void broadcast(String destination, Object payload) {
    messagingTemplate.convertAndSend(destination, payload);
  }
}
