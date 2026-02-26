package com.mudosa.musinsa.notification.event;

import com.mudosa.musinsa.chat.dto.MessageResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Qualifier("notificationEventPublisher")
@AllArgsConstructor
@Component
public class SpringNotificationEventPublisher implements NotificationEventPublisher {
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publishChatNotificationCreatedEvent(MessageResponse dto) {
    eventPublisher.publishEvent(new ChatNotificationCreatedEvent(dto));
  }
}
