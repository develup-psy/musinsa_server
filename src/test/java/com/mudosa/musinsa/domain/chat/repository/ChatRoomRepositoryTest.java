package com.mudosa.musinsa.domain.chat.repository;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.ServiceConfig;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatRoomRepository 테스트")
@Transactional
class ChatRoomRepositoryTest extends ServiceConfig {

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

  /* === findDistinctByParts_User_IdAndParts_DeletedAtIsNull 메서드 테스트 === */
  @Nested
  @DisplayName("사용자가 참여 중인 모든 채팅방 조회")
  class findDistinctByParts_User_IdAndParts_DeletedAtIsNull {

    @DisplayName("사용자가 참여 중인 채팅방을 모두 조회한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_Success() {
      //given
      // 1. 유저 저장
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");
      Brand brand3 = saveBrand("브랜드3", "Brand3");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand2, ChatRoomType.GROUP);
      ChatRoom chatRoom3 = saveChatRoom(brand3, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p1 = saveChatPart(chatRoom1, user);
      ChatPart p2 = saveChatPart(chatRoom2, user);

      //when
      List<ChatRoom> rooms =
          chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(user.getId());

      //then
      assertThat(rooms).hasSize(2)
          .containsExactlyInAnyOrder(chatRoom1, chatRoom2);
    }

    @DisplayName("사용자가 나간 채팅방은 조회 대상에서 제외한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_Delete_NotInclude() {
      //given
      // 1. 유저 저장
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom1 = saveChatRoom(brand1, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand2, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart chatPart1 = saveChatPart(chatRoom1, user);
      ChatPart chatPart2 = saveChatPart(chatRoom2, user);

      // 5. deleteAt 추가(퇴장)
      chatPart1.setDeletedAt(LocalDateTime.of(2025, 11, 9, 16, 30));
      chatPartRepository.save(chatPart1);

      //when
      List<ChatRoom> rooms =
          chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(user.getId());

      //then
      assertThat(rooms).hasSize(1)
          .containsExactly(chatRoom2);
    }

    @DisplayName("사용자의가 모든 채팅방을 나간 경우 빈 목록을 반환한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_AllDeleted_ReturnsEmpty() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);

      ChatPart part1 = saveChatPart(chatRoom1, user);
      ChatPart part2 = saveChatPart(chatRoom2, user);

      part1.setDeletedAt(LocalDateTime.now());
      part2.setDeletedAt(LocalDateTime.now());
      chatPartRepository.saveAll(List.of(part1, part2));

      // when
      List<ChatRoom> rooms = chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(user.getId());

      //then
      assertThat(rooms).isEmpty();
    }

    @DisplayName("존재하지 않는 사용자라면 빈 목록을 반환한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_NoUser() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);

      ChatPart part1 = saveChatPart(chatRoom1, user);
      ChatPart part2 = saveChatPart(chatRoom2, user);
      // when
      List<ChatRoom> rooms = chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(99999L);

      // then
      assertThat(rooms).isEmpty();
    }

    @DisplayName("채팅 참여 이력이 없는 사용자라면 빈 목록을 반환한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_NoPart() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);

      // when
      List<ChatRoom> rooms = chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(user.getId());

      // then
      assertThat(rooms).isEmpty();
    }

    @DisplayName("동일 채팅방에 대한 중복 참여가 있어도 채팅방은 한 번만 조회한다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_DuplicateParts_ReturnsDistinct() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      saveChatPart(chatRoom, user);
      saveChatPart(chatRoom, user);

      // when
      List<ChatRoom> rooms =
          chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(user.getId());

      // then
      assertThat(rooms).hasSize(1).containsExactly(chatRoom);
    }

    @DisplayName("사용자 본인만 소프트 삭제된 채팅방은 조회하지 않는다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_SelfDeleted_NotReturned() {
      // given
      User me = saveUser("user");
      User other = saveUser("other");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      ChatPart myPart = saveChatPart(chatRoom, me);
      ChatPart otherPart = saveChatPart(chatRoom, other);

      myPart.setDeletedAt(LocalDateTime.now());
      chatPartRepository.save(myPart);

      // when
      List<ChatRoom> rooms = chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(me.getId());

      //then
      assertThat(rooms).isEmpty();
    }

    @DisplayName("같은 채팅방의 다른 사용자가 소프트 삭제돼도 내 채팅방 조회에는 영향을 주지 않는다")
    @Test
    void findDistinctByParts_User_IdAndParts_DeletedAtIsNull_OtherUserDeleted_stillVisibleToMe() {
      // given
      User me = saveUser("me");
      User other = saveUser("other");

      Brand brand = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      ChatPart chatPart1 = saveChatPart(chatRoom, me);
      ChatPart chatPart2 = saveChatPart(chatRoom, other);

      chatPart1.setDeletedAt(LocalDateTime.of(2025, 11, 9, 0, 0));
      chatPartRepository.save(chatPart1);

      // when
      List<ChatRoom> rooms =
          chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(other.getId());


      // then
      assertThat(rooms).hasSize(1).containsExactly(chatRoom);
    }
  }
}
