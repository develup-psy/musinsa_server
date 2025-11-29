package com.mudosa.musinsa.domain.chat.event;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.domain.chat.dto.MessageResponse;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import com.mudosa.musinsa.domain.chat.entity.Message;
import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import com.mudosa.musinsa.domain.chat.enums.ChatPartRole;
import com.mudosa.musinsa.domain.chat.enums.ChatRoomType;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("MessageCreatedEventListener 테스트")
class MessageCreatedEventListenerTest extends ServiceConfig {

  @MockitoBean
  protected SimpMessagingTemplate messagingTemplate;

  @Autowired
  protected MessageCreatedEventListener messageCreatedEventListener;

  /**
   * === Test Helper ===
   */
  private User saveUser(String userName) {
    return User.create(userName, "pwd1234!", userName + "@test.com", UserRole.USER, String.format("http://mudosa/uploads/avatar/%s.png", userName), "010-0000-0000", "서울 강남구");
  }

  private Brand saveBrand(String nameKo, String nameEn) {
    return Brand.builder()
            .nameKo(nameKo)
            .nameEn(nameEn)
            .commissionRate(BigDecimal.valueOf(10.00))
            .status(BrandStatus.ACTIVE)
            .build();
  }

  private ChatRoom saveChatRoom(Brand brand) {
    return ChatRoom.builder()
            .brand(brand)
            .type(ChatRoomType.GROUP)
            .build();
  }

  private ChatPart saveChatPartOfUser(ChatRoom chatRoom, User user) {
    return ChatPart.builder()
            .chatRoom(chatRoom)
            .user(user)
            .role(ChatPartRole.USER)
            .build();
  }

  //메시지 생성
  private Message saveMessage(ChatPart chatPart, String content, LocalDateTime timestamp) {
    // 1. Message 생성 및 저장
    return Message.builder()
            .chatPart(chatPart)
            .content(content)
            .createdAt(timestamp)
            .build();

  }

  @Nested
  @DisplayName("메시지 웹소켓 전송")
  class handle {
    @DisplayName("메시지 생성 시 WebSocket으로 올바르게 전송된다")
    @Test
    void handle_shouldSendMessageOverWebSocket() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand);
      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);
      Message message = saveMessage(chatPart, "안녕", LocalDateTime.now());
      List<MessageAttachment> attachments = new ArrayList<>();
      MessageResponse dto = MessageResponse.from(message, attachments);
      MessageCreatedEvent event = new MessageCreatedEvent(dto);

      // when
      messageCreatedEventListener.handle(event);

      // then
      verify(messagingTemplate, times(1))
          .convertAndSend("/topic/chat/" + dto.getChatId(), dto);
    }
  }
}