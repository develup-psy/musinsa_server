package com.mudosa.musinsa.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringChatEventPublisher implements ChatEventPublisher {
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 파일 업로드 로직 실행 (비동기 처리용)
   */
  @Override
  public void publishUploadEvent(Long messageId, List<TempUploadedFile> files, String clientMessageId) {
    eventPublisher.publishEvent(new AttachmentUploadEvent(messageId, files, clientMessageId));
  }

  /**
   * 웹소켓 전송 요청 (트랜잭션 커밋 후 전송)
   */
  @Override
  public void publishBroadcastEvent(Long chatId, Object payload) {
    eventPublisher.publishEvent(new ChatBroadcastEvent(chatId, payload));
  }
}