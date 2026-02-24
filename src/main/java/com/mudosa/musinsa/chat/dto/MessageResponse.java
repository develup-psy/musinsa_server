package com.mudosa.musinsa.chat.dto;

import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.entity.MessageAttachment;
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
public class MessageResponse {
  @Schema(description = "메시지 id", example = "1")
  private Long messageId;
  @Schema(description = "채팅 id", example = "1")
  private Long chatId;
  @Schema(description = "참여 id", example = "1")
  private Long chatPartId;
  @Schema(description = "유저 id", example = "1")
  private Long userId;
  @Schema(description = "유저 이름", example = "홍길동")
  private String userName;
  @Schema(description = "메시지 내용", example = "안녕하세요!")
  private String content;
  @Schema(description = "메시지 내 첨부파일 리스트")
  private List<AttachmentResponse> attachments;
  @Schema(description = "보낸 시간", example = "2025-11-04T13:56:25.623Z")
  private LocalDateTime createdAt;
  @Schema(description = "삭제 여부", example = "false")
  private boolean isDeleted;

  @Schema(description = "답장 메시지")
  private ParentMessageResponse parent;

  @Schema(description = "매니저 여부", example = "false")
  private boolean isManager;

  public static MessageResponse from(Message message, List<MessageAttachment> attachments) {
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

      parentDto = ParentMessageResponse.of(parent, parentAttachmentDtos);
    }

    // 2) 현재 메시지 첨부 → DTO 변환
    List<AttachmentResponse> attachmentDtos = (attachments != null ? attachments : List.<MessageAttachment>of())
        .stream()
        .map(AttachmentResponse::of)
        .toList();

    // 3) 발신자/식별자
    Long chatId = message.getChatPart().getChatRoom().getChatId();
    Long chatPartId = message.getChatPart().getChatPartId();
    Long userId = message.getChatPart().getUser().getId();
    String userName = message.getChatPart().getUser().getUserName();

    return MessageResponse.builder()
        .messageId(message.getMessageId())
        .chatId(chatId)
        .chatPartId(chatPartId)
        .userId(userId)
        .userName(userName)
        .content(message.getContent())
        .attachments(attachmentDtos)
        .createdAt(message.getCreatedAt())
        .isDeleted(message.getDeletedAt() != null)
        .parent(parentDto)
        .build();
  }

  public static MessageResponse of(Message msg, List<AttachmentResponse> currentAttachments, ParentMessageResponse parentDto, boolean isManager) {
    return MessageResponse.builder()
        .messageId(msg.getMessageId())
        .chatId(msg.getChatId())
        .chatPartId(msg.getChatPart().getChatPartId())
        .userId(msg.getChatPart().getUser().getId())
        .userName(msg.getChatPart().getUser().getUserName())
        .content(msg.getContent())
        .attachments(currentAttachments)
        .createdAt(msg.getCreatedAt())
        .parent(parentDto)
        .isManager(isManager)
        .build();
  }

}
