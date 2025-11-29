package com.mudosa.musinsa.domain.chat.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import com.mudosa.musinsa.domain.chat.entity.Message;
import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import com.mudosa.musinsa.domain.chat.enums.ChatPartRole;
import com.mudosa.musinsa.domain.chat.enums.ChatRoomType;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageAttachmentRepository 테스트")
class MessageAttachmentRepositoryTest extends ServiceConfig {

  @AfterEach
  void tearDown() {
    // DB 초기화
    attachmentRepository.deleteAllInBatch();
    messageRepository.deleteAllInBatch();
    chatPartRepository.deleteAllInBatch();
    chatRoomRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
    brandRepository.deleteAllInBatch();
  }

  /* === Test Helper === */
  // 유저 생성 & 저장
  private User saveUser(String userName) {
    User user = User.create(userName, "yong1234!", "test@test.com", UserRole.USER, "http://mudosa/uploads/avatar/avatar1.png", "010-0000-0000", "서울 강남구");
    userRepository.save(user);
    return user;
  }

  // 브랜드 생성 & 저장
  private Brand saveBrand(String nameKo, String nameEn) {
    Brand brand = brandRepository.save(Brand.builder()
            .nameKo(nameKo)
            .nameEn(nameEn)
            .commissionRate(BigDecimal.valueOf(10.00))
            .status(BrandStatus.ACTIVE)
            .build());
    return brand;
  }

  // 채팅방 생성 & 저장
  private ChatRoom saveChatRoom(Brand brand, ChatRoomType type) {
    return chatRoomRepository.save(
        ChatRoom.builder()
                .brand(brand)
                .type(type)
                .build()
    );
  }

  // 채팅방 참여 정보 생성 & 저장
  private ChatPart saveChatPart(ChatRoom chatRoom, User user) {
    return chatPartRepository.save(
        ChatPart.builder()
                .chatRoom(chatRoom)
                .user(user)
                .role(ChatPartRole.USER)
                .build()
    );
  }

  //메시지 생성
  private Message saveMessageWithAttachments(ChatPart chatPart, String content, List<MessageAttachment> attachments) {
    // 1. Message 생성 및 저장
    Message message = Message.builder()
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();

    messageRepository.save(message); // id 확보

    // 2. 각 첨부파일에 message 연결
    for (MessageAttachment attachment : attachments) {
      attachment.setMessage(message);
      message.getAttachments().add(attachment);
    }

    // 3. 첨부파일 저장
    attachmentRepository.saveAll(attachments);

    return message;
  }

  private MessageAttachment createMessageAttachment(String fileName) {
    return MessageAttachment.builder()
            .attachmentUrl(String.format("http://mudosa/uploads/chat/1/%s.png", fileName))
            .mimeType("image/png")
            .sizeBytes(123L)
            .build();
  }

  /* === findAllByMessageIdIn 메서드 테스트 === */
  @Nested
  @DisplayName("메시지 ID 목록으로 첨부파일들을 조회한다")
  class findAllByMessageIdIn {
    @DisplayName("메시지 ID 목록으로 모든 첨부파일을 조회한다")
    @Test
    void findAllByMessageIdIn() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(m1_a1, m1_a2, m1_a3));

      // 메시지2 생성 (첨부 2)
      MessageAttachment m2_a1 = createMessageAttachment("image2_1");
      MessageAttachment m2_a2 = createMessageAttachment("image2_2");
      Message message2 = saveMessageWithAttachments(p, "안녕", List.of(m2_a1, m2_a2));

      Collection<Long> messageIds = List.of(message.getMessageId(), message2.getMessageId());

      // when
      List<MessageAttachment> allMessageAttachment = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(allMessageAttachment)
          .hasSize(5)
          .extracting("attachmentId")
          .containsExactlyInAnyOrder(m1_a1.getAttachmentId(), m1_a2.getAttachmentId(), m1_a3.getAttachmentId(), m2_a1.getAttachmentId(), m2_a2.getAttachmentId());
    }

    @DisplayName("메시지 ID 목록이 비어 있으면 빈 리스트를 반환한다")
    @Test
    void findAllByMessageIdIn_emptyIds() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(m1_a1, m1_a2, m1_a3));

      // when
      List<Long> messageIds = List.of();
      List<MessageAttachment> result = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(result).isEmpty();
    }

    @DisplayName("존재하지 않는 메시지 ID만 주면 빈 리스트를 반환한다")
    @Test
    void findAllByMessageIdIn_notFoundIds() {
      // given
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(m1_a1, m1_a2, m1_a3));

      List<Long> messageIds = List.of(999L, 1000L);

      // when
      List<MessageAttachment> result = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(result).isEmpty();
    }

    @DisplayName("첨부가 없는 메시지 ID가 섞여 있어도 첨부가 있는 메시지의 것만 조회된다")
    @Test
    void findAllByMessageIdIn_mixedWithMessageWithoutAttachments() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(m1_a1, m1_a2, m1_a3));

      // 메시지2 생성 (첨부 0)
      Message message2 = saveMessageWithAttachments(p, "안녕", List.of());

      Collection<Long> messageIds = List.of(message.getMessageId(), message2.getMessageId());

      // when
      List<MessageAttachment> allMessageAttachment = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(allMessageAttachment)
          .hasSize(3)
          .extracting("attachmentId")
          .containsExactlyInAnyOrder(m1_a1.getAttachmentId(), m1_a2.getAttachmentId(), m1_a3.getAttachmentId());
    }

    @DisplayName("중복된 메시지 ID가 들어와도 첨부파일은 중복 없이 조회된다")
    @Test
    void findAllByMessageIdIn_duplicatedIds() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(m1_a1, m1_a2, m1_a3));

      Collection<Long> messageIds = List.of(message.getMessageId(), message.getMessageId());

      // when
      List<MessageAttachment> allMessageAttachment = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(allMessageAttachment)
          .hasSize(3)
          .extracting("attachmentId")
          .containsExactlyInAnyOrder(m1_a1.getAttachmentId(), m1_a2.getAttachmentId(), m1_a3.getAttachmentId());
    }

    @DisplayName("채팅방이 달라도 메시지 ID가 일치하면 첨부파일을 조회한다")
    @Test
    void findAllByMessageIdIn_differentChatRooms() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand2, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);
      ChatPart p2 = saveChatPart(chatRoom2, user);

      // 메시지1 생성 (첨부 3)
      MessageAttachment c1_a1 = createMessageAttachment("image1_1");
      MessageAttachment c1_a2 = createMessageAttachment("image1_2");
      MessageAttachment c1_a3 = createMessageAttachment("image1_3");
      Message message = saveMessageWithAttachments(p, "안녕", List.of(c1_a1, c1_a2, c1_a3));

      MessageAttachment c2_a1 = createMessageAttachment("image2_1");
      MessageAttachment c2_a2 = createMessageAttachment("image2_2");
      Message message2 = saveMessageWithAttachments(p2, "안녕2", List.of(c2_a1, c2_a2));

      Collection<Long> messageIds = List.of(message.getMessageId(), message2.getMessageId());

      // when
      List<MessageAttachment> allMessageAttachment = attachmentRepository.findAllByMessageIdIn(messageIds);

      // then
      assertThat(allMessageAttachment)
          .hasSize(5)
          .extracting("attachmentId")
          .containsExactlyInAnyOrder(c1_a1.getAttachmentId(), c1_a2.getAttachmentId(), c1_a3.getAttachmentId()
              , c2_a1.getAttachmentId(), c2_a2.getAttachmentId());
    }
  }
}