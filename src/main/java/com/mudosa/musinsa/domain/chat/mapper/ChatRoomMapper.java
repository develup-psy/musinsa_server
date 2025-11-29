package com.mudosa.musinsa.domain.chat.mapper;

import com.mudosa.musinsa.domain.chat.dto.ChatRoomInfoResponse;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import org.springframework.stereotype.Component;

//response 변환 mapper로 분리
@Component
public class ChatRoomMapper {

  public ChatRoomInfoResponse toChatRoomInfoResponse(ChatRoom chatRoom, boolean isParticipate) {
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

  public ChatRoomInfoResponse toChatRoomInfoResponse(ChatRoom chatRoom,
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

