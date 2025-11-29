package com.mudosa.musinsa.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 채팅 참여자 정보 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "ChatPart Response Dto")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatPartResponse {
  @Schema(description = "참여 id", example = "1")
  private Long chatPartId;
  @Schema(description = "유저 id", example = "1")
  private Long userId;
  @Schema(description = "채팅 id", example = "1")
  private Long chatId;
  @Schema(description = "유저 이름", example = "홍길동")
  private String userName;
  @Schema(description = "채팅 내 역할", example = "MANAGER")
  private String role;
  @Schema(description = "참여 시간", example = "2025-11-04T13:56:25.623Z")
  private LocalDateTime createdAt;
  @Schema(description = "떠난 시간", example = "2025-11-14T15:56:25.923Z")
  private LocalDateTime deletedAt;

  /**
   * ChatPartResponse DTO 변환 메서드
   */
  public static ChatPartResponse of(ChatPart chatPart) {
    return ChatPartResponse.builder()
            .chatPartId(chatPart.getChatPartId())
            .userId(chatPart.getUser().getId())
            .chatId(chatPart.getChatRoom().getChatId())
            .userName(chatPart.getUser().getUserName())
            .createdAt(chatPart.getCreatedAt())
            .build();
  }
}
