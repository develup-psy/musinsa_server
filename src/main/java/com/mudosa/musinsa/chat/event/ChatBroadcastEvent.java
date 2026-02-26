package com.mudosa.musinsa.chat.event;

// [외부용] 웹소켓 브로드캐스트 이벤트
// "채팅방에 보낼 데이터"
public record ChatBroadcastEvent(
    // 채팅방 ID
    Long chatId,
    // 보내고자 하는 DTO
    // WSMessageResponseDTO OR WSFileUploadSuccessDTO
    Object payload
) {
}