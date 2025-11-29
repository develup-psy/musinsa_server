package com.mudosa.musinsa.domain.chat.event;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.domain.chat.dto.MessageResponse;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import com.mudosa.musinsa.domain.chat.entity.Message;
import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import com.mudosa.musinsa.domain.chat.enums.ChatPartRole;
import com.mudosa.musinsa.domain.chat.enums.ChatRoomType;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringMessageEventPublisher 테스트")
class SpringMessageEventPublisherTest {

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private SpringMessageEventPublisher messageEventPublisher;

  @BeforeEach
  void setUp() {
    messageEventPublisher = new SpringMessageEventPublisher(eventPublisher);
  }

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
  @DisplayName("메시지 이벤트 발행 테스트")
  class publishMessageCreated {
    @DisplayName("MessageResponse 전달 시 MessageCreatedEvent가 한 번 발행된다")
    @Test
    void publishMessageCreated_shouldCallPublishEvent() {
      // given
      // 유저 생성
      User user = saveUser("user");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart = saveChatPartOfUser(chatRoom1, user);
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message message = saveMessage(chatPart, "안녕", base);
      List<MessageAttachment> attachments = new ArrayList<>();
      MessageResponse dto = MessageResponse.from(message, attachments);

      // when
      messageEventPublisher.publishMessageCreated(dto);

      // then
      verify(eventPublisher, times(1)).publishEvent(any(MessageCreatedEvent.class));
    }
  }

}
