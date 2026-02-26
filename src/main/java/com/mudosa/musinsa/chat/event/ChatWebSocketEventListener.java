package com.mudosa.musinsa.chat.event;

import com.mudosa.musinsa.chat.broker.ChatMessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketEventListener {
  private final ChatMessageBroker chatMessageBroker;

  /**
   * DB 트랜잭션이 안전하게 커밋된 후에만 클라이언트에게 전송
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleBroadcast(ChatBroadcastEvent event) {
    String destination = "/topic/chat." + event.chatId();

    log.debug("[WSEvent] 브로드캐스트 전송 -> dest={}, payload={}", destination, event.payload());

    chatMessageBroker.sendToTopic(destination, event.payload());
  }
}