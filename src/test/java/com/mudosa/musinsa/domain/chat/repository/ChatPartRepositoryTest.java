package com.mudosa.musinsa.domain.chat.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// TODO: @katsudon8991 - findChatPartsExcludingUser 메서드 테스트 작성 필요
@DisplayName("ChatPartRepository 테스트")
class ChatPartRepositoryTest extends ServiceConfig {

  @AfterEach
  void tearDown() {
    // DB 초기화
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
    return brandRepository.save(Brand.builder()
            .nameKo(nameKo)
            .nameEn(nameEn)
            .commissionRate(BigDecimal.valueOf(10.00))
            .status(BrandStatus.ACTIVE)
            .build());
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

  @Nested
  @DisplayName("채팅방에 어떤 참여자를 제외한 ChatPart 정보를 조회한다")
  class findChatPartExcludingUser {
      @Test
      void findChatPartExcludingUserTest(){
          // given
          User user1 = saveUser("user1");
          User user2 = saveUser("user2");

          Brand brand1 = saveBrand("브랜드1", "brand1");

          ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

          saveChatPart(chatRoom1, user1);
          saveChatPart(chatRoom1, user2);
          // when
          List<ChatPart> chatParts = chatPartRepository.findChatPartsExcludingUser(user1.getId(), chatRoom1.getChatId());
          // then
          assertThat(chatParts).hasSize(1);

      }
  }

  /* === countByChatRoom_ChatIdAndDeletedAtIsNull 메서드 테스트 === */
  @Nested
  @DisplayName("채팅방 참여자 수 조회")
  class countByChatRoom_ChatIdAndDeletedAtIsNull {
    @DisplayName("채팅방에 참여중인 참여자 수를 반환한다")
    @Test
    void countByChatRoom_ChatIdAndDeletedAtIsNull_Success() {
      // given
      //1. 유저 생성
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // 4. 참가자 저장
      saveChatPart(chatRoom1, user1);
      saveChatPart(chatRoom1, user2);

      // when
      long countPart = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(chatRoom1.getChatId());

      // then
      assertThat(countPart)
          .isEqualTo(2L);
    }

    @DisplayName("퇴장한 참여자를 제외한 채팅방에 참여중인 참여자 수를 반환한다")
    @Test
    void countByChatRoom_ChatIdAndDeletedAtIsNull_DeleteAt_NotCount() {
      // given
      //1. 유저 생성
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p1 = saveChatPart(chatRoom1, user1);
      saveChatPart(chatRoom1, user2);

      // 5. deleteAt 추가(퇴장)
      p1.setDeletedAt(LocalDateTime.of(2025, 11, 19, 0, 0));
      chatPartRepository.save(p1);

      // when
      long countPart = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(chatRoom1.getChatId());

      // then
      assertThat(countPart)
          .isEqualTo(1L);
    }

    @DisplayName("아무도 참여하지 않은 채팅방이면 0L을 반환한다")
    @Test
    void countByChatRoom_ChatIdAndDeletedAtIsNull_NotPart_ReturnsZero() {
      // given
      // 1. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 2. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // when
      long countPart = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(chatRoom1.getChatId());

      // then
      assertThat(countPart)
          .isZero();
    }

    @DisplayName("여러 채팅방이 있어도 채팅방별로 참여자 수를 센다")
    @Test
    void countByChatRoom_ChatIdAndDeletedAtIsNull_SplitByRoom() {
      // given
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드1", "Brand1");

      ChatRoom room1 = saveChatRoom(brand1, ChatRoomType.GROUP);
      ChatRoom room2 = saveChatRoom(brand2, ChatRoomType.GROUP);

      saveChatPart(room1, user1);
      saveChatPart(room1, user2);
      // 다른 방 참여(user1)
      saveChatPart(room2, user1);

      // when
      long countRoom1 = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(room1.getChatId());
      long countRoom2 = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(room2.getChatId());

      // then
      assertThat(countRoom1).isEqualTo(2L);
      assertThat(countRoom2).isEqualTo(1L);
    }

    @DisplayName("한 사용자가 퇴장해도 다른 사용자는 참여자로 집계된다")
    @Test
    void countByChatRoom_ChatIdAndDeletedAtIsNull_OneDeleted_OthersRemain() {
      // given
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");

      Brand brand = saveBrand("브랜드1", "Brand1");

      ChatRoom room = saveChatRoom(brand, ChatRoomType.GROUP);

      ChatPart part1 = saveChatPart(room, user1);
      saveChatPart(room, user2);

      part1.setDeletedAt(LocalDateTime.of(2025, 11, 19, 0, 0));
      chatPartRepository.save(part1);

      // when
      long count = chatPartRepository.countByChatRoom_ChatIdAndDeletedAtIsNull(room.getChatId());

      //then
      assertThat(count).isEqualTo(1L);
    }
  }

  /* === findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull 메서드 테스트 === */
  @Nested
  @DisplayName("참여 정보 조회")
  class findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull {

    @DisplayName("참여 중이면 Optional로 참여 정보를 반환한다")
    @Test
    void findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull_Exists_ReturnsChatPart() {
      // given
      //1. 유저 생성
      User user = saveUser("user1");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p1 = saveChatPart(chatRoom1, user);

      // when
      Optional<ChatPart> chatPart = chatPartRepository.findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(chatRoom1.getChatId(), user.getId());

      // then
      assertThat(chatPart)
          .isPresent()
          .get()
          .extracting(ChatPart::getChatPartId)
          .isEqualTo(p1.getChatPartId());
    }

    @DisplayName("참여 중이 아니면 Optional.empty()를 반환한다")
    @Test
    void findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull_NotExists_ReturnsEmpty() {
      // given
      //1. 유저 생성
      User user = saveUser("user1");

      // 2. 브랜드 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand2, ChatRoomType.GROUP);

      // 4. 참가자 저장
      saveChatPart(chatRoom1, user);

      // when
      Optional<ChatPart> chatPart = chatPartRepository.findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(chatRoom2.getChatId(), user.getId());

      // then
      assertThat(chatPart).isEmpty();
    }

    @DisplayName("퇴장한 참여라면 Optional.empty()를 반환한다")
    @Test
    void findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull_DeleteAt_ReturnsEmpty() {
      // given
      //1. 유저 생성
      User user = saveUser("user1");

      // 2. 브랜드 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p1 = saveChatPart(chatRoom1, user);

      // 5. deleteAt 추가(퇴장)
      p1.setDeletedAt(LocalDateTime.of(2025, 11, 19, 0, 0));
      chatPartRepository.save(p1);

      // when
      Optional<ChatPart> chatPart = chatPartRepository.findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(chatRoom1.getChatId(), user.getId());

      // then
      assertThat(chatPart).isEmpty();
    }

    @DisplayName("같은 채팅방에 여러 사용자가 있어도 조회 대상 사용자의 참여만 반환한다")
    @Test
    void findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull_MultipleUsers_ReturnsOnlyTarget() {
      // given
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom room = saveChatRoom(brand, ChatRoomType.GROUP);

      saveChatPart(room, user1);
      saveChatPart(room, user2);

      // when
      Optional<ChatPart> chatPart =
          chatPartRepository.findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(room.getChatId(), user1.getId());

      // then
      assertThat(chatPart).isPresent()
          .get()
          .extracting(ChatPart::getUser)
          .extracting(User::getId)
          .isEqualTo(user1.getId());
    }
  }

  /* === existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull 메서드 테스트 === */
  @Nested
  @DisplayName("채팅방 참여 여부 조회")
  class existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull {

    @DisplayName("참여 중이면 true를 반환한다")
    @Test
    void existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull_Part_ReturnsTrue() {
      // given
      // 1. 유저 저장
      User user = saveUser("user1");

      // 2. 브랜드 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);

      // 4. 참가자 저장
      saveChatPart(chatRoom1, user);

      // when
      boolean isPart = chatPartRepository.existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(chatRoom1.getChatId(), user.getId());

      // then
      assertThat(isPart).isTrue();
    }

    @DisplayName("참여 중이 아니면 false를 반환한다")
    @Test
    void existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull_NotPart_ReturnsFalse() {
      // given
      // 1. 유저 저장
      User user = saveUser("user1");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand2, ChatRoomType.GROUP);

      // 4. 참가자 저장
      saveChatPart(chatRoom1, user);

      // when
      boolean isPart = chatPartRepository.existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(chatRoom2.getChatId(), user.getId());

      // then
      assertThat(isPart).isFalse();
    }

    @DisplayName("퇴장한 참여라면 false를 반환한다")
    @Test
    void existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull_DeleteAt_ReturnsFalse() {
      // given
      // 1. 유저 저장
      User user = saveUser("user1");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p1 = saveChatPart(chatRoom, user);

      // 5. deleteAt 추가(퇴장)
      p1.setDeletedAt(LocalDateTime.of(2025, 11, 19, 0, 0));
      chatPartRepository.save(p1);

      // when
      boolean isPart1 = chatPartRepository.existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(chatRoom.getChatId(), user.getId());

      // then
      assertThat(isPart1).isFalse();
    }
  }
}