package com.mudosa.musinsa.chat.event;

import java.util.List;

public interface ChatEventPublisher {
  void publishUploadEvent(Long messageId, List<TempUploadedFile> files, String clientMessageId);

  void publishBroadcastEvent(Long chatId, Object payload);
}
