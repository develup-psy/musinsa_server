package com.mudosa.musinsa.chat.event;

import com.mudosa.musinsa.chat.service.AttachmentUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSystemEventListener {
  private final AttachmentUploadService attachmentUploadService;

  /**
   * Facade 트랜잭션이 커밋된 후 -> 별도 스레드에서 파일 업로드 시작
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleFileUpload(AttachmentUploadEvent event) {
    attachmentUploadService.saveAttachments(event.messageId(), event.files(), event.clientMessageId());
  }
}