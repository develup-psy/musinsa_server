package com.mudosa.musinsa.notification.event;

import com.mudosa.musinsa.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase= TransactionPhase.AFTER_COMMIT)
    public void handle(ChatNotificationCreatedEvent event){
        notificationService.createChatNotification(event);
    }
}
