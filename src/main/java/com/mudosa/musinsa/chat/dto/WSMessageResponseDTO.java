package com.mudosa.musinsa.chat.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.entity.MessageAttachment;
import com.mudosa.musinsa.chat.enums.MessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Message Response Dto")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSMessageResponseDTO {
  private String type = "MESSAGE";

  @Schema(description = "메시지 id", example = "1")
  private Long messageId;
  @Schema(description = "채팅 id", example = "1")
  private Long chatId;
  @Schema(description = "유저 id", example = "1")
  private Long userId;
  @Schema(description = "유저 이름", example = "홍길동")
  private String userName;
  @Schema(description = "메시지 내용", example = "안녕하세요!")
  private String content;
  @Schema(description = "보낸 시간", example = "2025-11-04T13:56:25.623Z")
  private LocalDateTime createdAt;

  @Schema(description = "답장 메시지")
  private ParentMessageResponse parent;

  @Schema(description = "매니저 여부", example = "false")
  private boolean isManager;
  @Schema(description = "메시지 상태", example = "NORMAL")
  private MessageStatus status;

  @Schema(description = "동일 메시지 여부 구분 id", example = "UUID-1111-1234")
  private String clientMessageId;

  public static WSMessageResponseDTO of(Message message) {
    // 1) 부모 메시지 취득 (없을 수 있음)
    Message parent = message.getParent();
    ParentMessageResponse parentDto = null;

    if (parent != null) {
      // 부모 첨부 → DTO 변환
      List<MessageAttachment> parentAtt = parent.getAttachments() != null
          ? parent.getAttachments()
          : List.of();

      List<AttachmentResponse> parentAttachmentDtos = parentAtt.stream()
          .map(AttachmentResponse::of)
          .toList();

      parentDto = ParentMessageResponse.builder()
          .messageId(parent.getMessageId())
          .userName(parent.getChatPart().getUser().getUserName())
          .content(parent.getContent())
          .attachments(parentAttachmentDtos)
          .build();
    }

    // 3) 발신자/식별자
    Long chatId = message.getChatId();
    Long userId = message.getChatPart().getUser().getId();
    String userName = message.getChatPart().getUser().getUserName();

    return WSMessageResponseDTO.builder()
        .messageId(message.getMessageId())
        .chatId(chatId)
        .userId(userId)
        .userName(userName)
        .content(message.getContent())
        .createdAt(message.getCreatedAt())
        .parent(parentDto)
        .status(message.getStatus())
        .build();
  }

  public static WSMessageResponseDTO of(Message message, String clientMessageId) {
    // 1) 부모 메시지 취득 (없을 수 있음)
    Message parent = message.getParent();
    ParentMessageResponse parentDto = null;

    if (parent != null) {
      // 부모 첨부 → DTO 변환
      List<MessageAttachment> parentAtt = parent.getAttachments() != null
          ? parent.getAttachments()
          : List.of();

      List<AttachmentResponse> parentAttachmentDtos = parentAtt.stream()
          .map(AttachmentResponse::of)
          .toList();

      parentDto = ParentMessageResponse.builder()
          .messageId(parent.getMessageId())
          .userName(parent.getChatPart().getUser().getUserName())
          .content(parent.getContent())
          .attachments(parentAttachmentDtos)
          .build();
    }

    // 3) 발신자/식별자
    Long chatId = message.getChatId();
    Long userId = message.getChatPart().getUser().getId();
    String userName = message.getChatPart().getUser().getUserName();

    return WSMessageResponseDTO.builder()
        .type("M")
        .messageId(message.getMessageId())
        .chatId(chatId)
        .userId(userId)
        .userName(userName)
        .content(message.getContent())
        .createdAt(message.getCreatedAt())
        .parent(parentDto)
        .status(message.getStatus())
        .clientMessageId(clientMessageId)
        .build();
  }

}
