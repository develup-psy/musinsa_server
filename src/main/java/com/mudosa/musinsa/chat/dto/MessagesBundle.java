package com.mudosa.musinsa.chat.dto;

import com.mudosa.musinsa.chat.entity.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record MessagesBundle(
    List<Message> messages,
    boolean hasNext,
    Set<Long> managerUserIds,
    Map<Long, List<AttachmentResponse>> attachmentMap,
    Map<Long, Message> parentMap
) {

  public static MessagesBundle empty() {
    return new MessagesBundle(
        List.of(),
        false,
        Set.of(),
        Map.of(),
        Map.of()
    );
  }
}