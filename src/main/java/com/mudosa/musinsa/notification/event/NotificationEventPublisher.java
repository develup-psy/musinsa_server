package com.mudosa.musinsa.notification.event;


import com.mudosa.musinsa.chat.dto.MessageResponse;

public interface NotificationEventPublisher {
  void publishChatNotificationCreatedEvent(MessageResponse dto);
}
