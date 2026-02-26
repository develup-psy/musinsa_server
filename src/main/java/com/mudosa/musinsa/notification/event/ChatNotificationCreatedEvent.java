package com.mudosa.musinsa.notification.event;


import com.mudosa.musinsa.chat.dto.MessageResponse;

public record ChatNotificationCreatedEvent(MessageResponse messageResponse, Long timestamp) {
  public ChatNotificationCreatedEvent(MessageResponse messageResponse) {
    this(messageResponse, System.currentTimeMillis());
  }

  public Long getUserId() {
    return messageResponse.getUserId();
  }

  public Long getChatId() {
    return messageResponse.getChatId();
  }

  public String getContent() {
    return messageResponse.getContent();
  }
}
