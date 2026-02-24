package com.mudosa.musinsa.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mudosa.musinsa.chat.entity.ChatRoom;
import com.mudosa.musinsa.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 채팅방 정보 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "ChatRoom Info Response Dto")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRoomInfoResponse {
  @Schema(description = "채팅 id", example = "1")
  private Long chatId;
  @Schema(description = "채팅방 타입", example = "GROUP")
  private ChatRoomType type;
  @Schema(description = "참여자 수", example = "1000")
  private Long partNum;
  @Schema(description = "마지막 메시지 시간", example = "2025-11-04T13:56:25.623Z")
  private LocalDateTime lastMessageAt;

  @Schema(description = "브랜드 id", example = "1")
  private Long brandId;
  @Schema(description = "브랜드 한글 이름", example = "무신상")
  private String brandNameKo;

  @Schema(description = "채팅방 로고 Url", example = "/brand/1/7aa18c86-68e5-43c5-8c53-f3a12f914500_무신상.jpeg")
  private String logoUrl;

  @Schema(description = "참여 여부", example = "false")
  private boolean isParticipate;

  public static ChatRoomInfoResponse of(ChatRoom chatRoom, boolean isParticipate) {
    return ChatRoomInfoResponse.builder()
        .chatId(chatRoom.getChatId())
        .brandId(chatRoom.getBrand().getBrandId())
        .brandNameKo(chatRoom.getBrand().getNameKo())
        .type(chatRoom.getType())
        .lastMessageAt(chatRoom.getLastMessageAt())
        .partNum(null)
        .isParticipate(isParticipate)
        .logoUrl(chatRoom.getBrand().getLogoUrl())
        .build();
  }

  public static ChatRoomInfoResponse of(ChatRoom chatRoom,
                                        boolean isParticipate,
                                        long partNum) {
    return ChatRoomInfoResponse.builder()
        .brandId(chatRoom.getBrand().getBrandId())
        .brandNameKo(chatRoom.getBrand().getNameKo())
        .chatId(chatRoom.getChatId())
        .type(chatRoom.getType())
        .partNum(partNum)
        .lastMessageAt(chatRoom.getLastMessageAt())
        .isParticipate(isParticipate)
        .build();
  }
}
