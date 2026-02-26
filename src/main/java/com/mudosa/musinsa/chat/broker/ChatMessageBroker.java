package com.mudosa.musinsa.chat.broker;

public interface ChatMessageBroker {
  /**
   * 구독자에게 전달
   */
  void sendToTopic(String destination, Object payload);

  /**
   * 유저에게 전달
   */
  void sendToUser(String sessionId, String destination, Object payload);

  /**
   * 전체 전달
   */
  void broadcast(String destination, Object payload);
}
