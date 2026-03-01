package com.mudosa.musinsa.chat.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.chat.entity.ChatPart;
import com.mudosa.musinsa.chat.entity.ChatRoom;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.enums.ChatPartRole;
import com.mudosa.musinsa.chat.enums.ChatRoomType;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("MessageRepository 테스트")
@Transactional
class MessageRepositoryTest extends ServiceConfig {

  /* === Test Helper === */
  // 유저 생성 & 저장`
  private User saveUser(String userName) {
    User user = User.create(
        userName,
        "yong1234!",
        "test@test.com",
        UserRole.USER,
        "http://mudosa/uploads/avatar/avatar1.png",
        "010-0000-0000",
        "서울 강남구"
    );
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

  // 메시지 생성
  private Message saveMessage(ChatPart chatPart, String content, LocalDateTime timestamp) {
    Message message = Message.builder()
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content(content)
            .createdAt(timestamp)
            .build();

    messageRepository.save(message);
    return message;
  }

  private Message saveMessageWithParent(ChatPart chatPart, String content, LocalDateTime timestamp, Message parent) {
    Message message = Message.builder()
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content(content)
            .parent(parent)
            .createdAt(timestamp)
            .build();

    messageRepository.save(message);
    return message;
  }

  /* === findPageWithRelationsByChatId 메서드 테스트  === */
  @Nested
  @DisplayName("채팅방 메시지 페이징 조회")
  class findPageWithRelationsByChatId {

    /**
     * 첫 페이지이면서, 다음 페이지가 있는 경우 검증
     */
    private static void assertSlice_hasNext(Slice<Message> messages, int size, int totalCount, int page) {
      // 요청한 페이지/사이즈 그대로 들어왔는지
      assertThat(messages.getNumber()).isEqualTo(page);
      assertThat(messages.getSize()).isEqualTo(size);

      // 첫 페이지라 이전 페이지는 없음
      assertThat(messages.hasPrevious()).isFalse();

      // 이 테스트에서는 항상 "가득 찬 페이지 + 다음 페이지 존재" 시나리오
      assertThat(messages.getNumberOfElements()).isEqualTo(size);
      assertThat(messages.hasNext()).isTrue();

      // 전체 개수를 알고 있으니, 기대값도 계산해 볼 수 있음
      boolean expectedHasNext = totalCount > (page + 1) * size;
      assertThat(messages.hasNext()).isEqualTo(expectedHasNext);
    }

    /**
     * 마지막 페이지(또는 전체 개수가 size 이하인 케이스) 검증
     */
    private static void assertSlice_theEnd(Slice<Message> messages, int count, int page, int size) {
      // 현재 페이지의 실제 요소 수 = 총 메시지 수 (마지막 페이지라서)
      assertThat(messages.getNumberOfElements()).isEqualTo(count);

      assertThat(messages.getNumber()).isEqualTo(page);
      assertThat(messages.getSize()).isEqualTo(size);

      // 마지막 페이지이므로 다음 페이지 없음
      assertThat(messages.hasNext()).isFalse();
      // 테스트에서는 page=0인 케이스만 사용하므로 이전 페이지도 없음
      assertThat(messages.hasPrevious()).isFalse();
    }

    @DisplayName("메시지가 존재할 경우 최신순으로 정렬된 결과를 반환한다")
    @Test
    void findPageWithRelationsByChatId_Success() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지 30건 생성
      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= count; i++) {
        saveMessage(p, "안녕" + i, base.plusSeconds(i));
      }

      int page = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(page, size);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      // Slice 메타 검증
      assertSlice_hasNext(messages, size, count, page);

      // 최신순 정렬 검증
      assertThat(messages.getContent())
          .extracting(Message::getContent)
          .containsExactly(
              "안녕30", "안녕29", "안녕28", "안녕27", "안녕26",
              "안녕25", "안녕24", "안녕23", "안녕22", "안녕21"
          );
      assertThat(messages.getContent())
          .extracting(Message::getMessageId)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }


    @DisplayName("같은 시간 메시지가 존재할 경우 messageId순으로 정렬된 결과를 반환한다")
    @Test
    void findPageWithRelationsByChatId_withSameTime() {
      // given
      //1. 유저 생성
      User user = saveUser("user");

      // 2. 브랜드 먼저 저장
      Brand brand = saveBrand("브랜드", "Brand");

      // 3. 이제 이 '영속된' 브랜드들을 채팅방에 달아준다
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);

      // 4. 참가자 저장
      ChatPart p = saveChatPart(chatRoom, user);

      // 메시지 30건 생성
      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= count; i++) {
        saveMessage(p, "안녕" + i, base);
      }

      int page = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(page, size);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_hasNext(messages, size, count, page);

      assertThat(messages.getContent())
          .extracting(Message::getContent)
          .containsExactly(
              "안녕30", "안녕29", "안녕28", "안녕27", "안녕26",
              "안녕25", "안녕24", "안녕23", "안녕22", "안녕21"
          );
      assertThat(messages.getContent())
          .extracting(Message::getMessageId)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @DisplayName("메시지가 존재하지 않으면 빈 Slice를 반환한다")
    @Test
    void findPage_emptyResult() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      saveChatPart(chatRoom, user);

      int count = 0;
      int page = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(page, size);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_theEnd(messages, count, page, size);
      assertThat(messages.getContent()).isEmpty();
    }

    @Test
    @DisplayName("메시지 개수가 페이지 크기보다 적을 경우, 모든 메시지를 반환한다")
    void findPage_lessMessageThanSize() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      int count = 1;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      saveMessage(p, "안녕" + count, base);

      int page = 0;
      int size = 2;
      Pageable pageable = PageRequest.of(page, size);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_theEnd(messages, count, page, size);

      assertThat(messages.getContent())
          .hasSize(count)
          .extracting(Message::getContent)
          .containsExactly("안녕1");
    }

    @DisplayName("여러 채팅방이 존재해도 조회한 채팅방의 메시지만 페이징된다")
    @Test
    void findPageWithRelationsByChatId_ignoreOtherChatRooms() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p1 = saveChatPart(chatRoom1, user);

      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p2 = saveChatPart(chatRoom2, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      int chat1messageNum = 5;
      for (int i = 1; i <= chat1messageNum; i++) {
        saveMessage(p1, "room1-" + i, base.plusSeconds(i));
      }

      int chat2messageNum = 10;
      for (int i = 1; i <= chat2messageNum; i++) {
        saveMessage(p2, "room2-" + i, base.plusSeconds(i));
      }

      int page = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(page, size);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom1.getChatId(), pageable);

      // then
      // 이 Slice에는 room1 메시지만 들어 있어야 하고, 개수는 chat1messageNum
      assertThat(messages.getNumberOfElements()).isEqualTo(chat1messageNum);
      assertThat(messages.hasNext()).isFalse();

      assertThat(messages.getContent())
          .extracting(Message::getContent)
          .allMatch(c -> c.startsWith("room1-"));
    }

    @DisplayName("첫 번째 페이지가 아닌 페이지를 조회할 때도 최신순이 유지된다")
    @Test
    void findPage_noFirstPage() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      int count = 25;
      for (int i = 1; i <= count; i++) {
        saveMessage(p, "안녕" + i, base.plusSeconds(i));
      }

      Pageable pageable = PageRequest.of(1, 10);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertThat(messages.getNumber()).isEqualTo(1);
      assertThat(messages.hasNext()).isTrue(); // 25건 → 0,1,2 페이지 존재

      assertThat(messages.getContent())
          .extracting(Message::getContent)
          .containsExactly(
              "안녕15", "안녕14", "안녕13", "안녕12", "안녕11",
              "안녕10", "안녕9", "안녕8", "안녕7", "안녕6"
          );
      assertThat(messages.getContent())
          .extracting(Message::getCreatedAt)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @DisplayName("메시지와 함께 작성자 정보도 로딩된다")
    @Test
    void findPageWithUserFetched() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      saveMessage(p, "안녕", base);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertThat(messages.getContent().getFirst().getChatPart().getUser().getUserName())
          .isEqualTo(user.getUserName());
      assertThat(messages.getContent().getFirst().getChatPart().getUser().getId())
          .isEqualTo(user.getId());
    }

    @DisplayName("존재하지 않는 채팅방 ID로 조회해도 빈 Slice를 반환한다")
    @Test
    void findPage_notExistsChatId() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      saveMessage(p, "안녕", base);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(999999L, pageable);

      // then
      assertThat(messages.getContent()).isEmpty();
      assertThat(messages.getNumberOfElements()).isZero();
      assertThat(messages.hasNext()).isFalse();
    }

    @DisplayName("답장 메시지를 조회할 때 부모 메시지도 함께 조회된다")
    @Test
    void findPage_withParentMessage() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message parent = saveMessage(p, "부모", base);
      saveMessageWithParent(p, "자식", base.plusSeconds(1), parent);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertThat(messages.getContent().getFirst().getParent()).isNotNull();
      assertThat(messages.getContent().getFirst().getParent().getContent()).isEqualTo("부모");
      assertThat(messages.getContent().getFirst().getContent()).isEqualTo("자식");
    }

    @DisplayName("삭제된 메시지는 조회 결과에 포함되지 않는다")
    @Test
    void findPage_excludeDeletedMessages() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message alive = saveMessage(p, "alive", base.plusSeconds(1));
      Message deleted = saveMessage(p, "delete", base.plusSeconds(2));

      deleted.setDeletedAt(LocalDateTime.now());
      messageRepository.save(deleted);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Message> messages = messageRepository.findSliceWithRelationsByChatId(chatRoom.getChatId(), pageable);

      // then
      assertThat(messages.getNumberOfElements()).isEqualTo(1);
      assertThat(messages.getContent())
          .extracting(Message::getContent)
          .containsExactly("alive");
    }
  }

  @Nested
  @DisplayName("최신순 첫 페이지 ID를 최신순으로 반환한다")
  class findIdSliceByChatId {
    /* === helper === */

    /**
     * 첫 페이지이면서, 다음 페이지가 있는 경우 검증
     */
    private static void assertSlice_hasNext(Slice<Long> messages, int size, int count) {
      // 요청한 사이즈 그대로 들어왔는지
      assertThat(messages.getSize()).isEqualTo(size);

      // 첫 페이지라 이전 페이지는 없음
      assertThat(messages.hasPrevious()).isFalse();

      // 이 테스트에서는 항상 "가득 찬 페이지 + 다음 페이지 존재" 시나리오
      assertThat(messages.getNumberOfElements()).isEqualTo(size);
      assertThat(messages.hasNext()).isTrue();

      // 전체 개수를 알고 있으니, 기대값도 계산해 볼 수 있음
      boolean expectedHasNext = (count > size);
      assertThat(messages.hasNext()).isEqualTo(expectedHasNext);
    }

    /**
     * 마지막 페이지(또는 전체 개수가 size 이하인 케이스) 검증
     */
    private static void assertSlice_theEnd(Slice<Long> messages, int count, int size) {
      // 현재 페이지의 실제 요소 수 = 총 메시지 수 (마지막 페이지라서)
      assertThat(messages.getNumberOfElements()).isEqualTo(count);

      assertThat(messages.getSize()).isEqualTo(size);

      // 마지막 페이지이므로 다음 페이지 없음
      assertThat(messages.hasNext()).isFalse();
    }

    @DisplayName("메시지가 존재할 경우 최신순(messageId)으로 정렬된 id 결과를 반환한다")
    @Test
    void findIdSliceByChatId_Success() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      // 생성된 메시지 ID를 순서대로 저장
      List<Long> createdMessageIds = new ArrayList<>();
      for (int i = 1; i <= count; i++) {
        Message message = saveMessage(p, String.valueOf(i), base.plusSeconds(i));
        createdMessageIds.add(message.getMessageId());
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      // 실제 생성된 ID 중 최신 10개 추출 (역순)
      List<Long> expectedIds = createdMessageIds.stream()
          .sorted(Comparator.reverseOrder())  // 최신순 정렬
          .limit(size)
          .toList();

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_hasNext(messages, size, count);

      assertThat(messages.getContent())
          .hasSize(size)
          .containsExactlyElementsOf(expectedIds)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @DisplayName("메시지가 존재하지 않으면 빈 Slice를 반환한다")
    @Test
    void findIdSliceByChatId_emptyResult() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      saveChatPart(chatRoom, user);

      int count = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_theEnd(messages, count, size);
      assertThat(messages.getContent()).isEmpty();
    }

    @DisplayName("메시지 개수가 페이지 크기보다 적을 경우, 모든 메시지 id를 반환한다")
    @Test
    void findIdSliceByChatId_lessMessageThanSize() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      int count = 1;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      // 생성된 메시지 저장
      Message message = saveMessage(p, String.valueOf(count), base);

      int size = 2;
      Pageable pageable = PageRequest.of(0, size);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(chatRoom.getChatId(), pageable);

      // then
      assertSlice_theEnd(messages, count, size);

      // 실제 생성된 메시지 ID 사용
      assertThat(messages.getContent())
          .hasSize(count)
          .containsExactly(message.getMessageId());
    }

    @DisplayName("여러 채팅방이 존재해도 조회한 채팅방의 메시지만 페이징된다")
    @Test
    void findIdSliceByChatId_ignoreOtherChatRooms() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p1 = saveChatPart(chatRoom1, user);

      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p2 = saveChatPart(chatRoom2, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      // 🔹 각 채팅방의 메시지 ID를 저장해두면 검증하기 좋음
      List<Long> chatRoom1Ids = new ArrayList<>();
      int chat1messageNum = 5;
      for (int i = 1; i <= chat1messageNum; i++) {
        Long id = saveMessage(p1, "room1-" + i, base.plusSeconds(i))
            .getMessageId();
        chatRoom1Ids.add(id);
      }

      List<Long> chatRoom2Ids = new ArrayList<>();
      int chat2messageNum = 10;
      for (int i = 1; i <= chat2messageNum; i++) {
        Long id = saveMessage(p2, "room2-" + i, base.plusSeconds(i))
            .getMessageId();
        chatRoom2Ids.add(id);
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(chatRoom1.getChatId(), pageable);

      // then
      // 오직 chatRoom1의 5개만 조회
      assertThat(messages.getNumberOfElements()).isEqualTo(chat1messageNum);
      assertThat(messages.hasNext()).isFalse();

      // 슬라이스에 담긴 ID는 전부 chatRoom1에 속한 메시지여야 한다
      assertThat(messages.getContent())
          .hasSize(chat1messageNum)
          .containsExactlyElementsOf(
              chatRoom1Ids.stream()
                  .sorted(Comparator.reverseOrder()) // createdAt desc, messageId desc 기준으로 최신순
                  .toList()
          )
          .doesNotContainAnyElementsOf(chatRoom2Ids); // chatRoom2 메시지는 절대

    }

    @DisplayName("존재하지 않는 채팅방 ID로 조회해도 빈 Slice를 반환한다")
    @Test
    void findIdSliceByChatId_notExistsChatId() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      saveMessage(p, "안녕", base);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(999999L, pageable);

      // then
      assertThat(messages.getContent()).isEmpty();
      assertThat(messages.getNumberOfElements()).isZero();
      assertThat(messages.hasNext()).isFalse();
    }

    @DisplayName("삭제된 메시지는 조회 결과에 포함되지 않는다")
    @Test
    void findIdSliceByChatId_excludeDeletedMessages() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message alive = saveMessage(p, "alive", base.plusSeconds(1));
      Message deleted = saveMessage(p, "delete", base.plusSeconds(2));

      deleted.setDeletedAt(LocalDateTime.now());
      messageRepository.save(deleted);

      Pageable pageable = PageRequest.of(0, 10);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatId(chatRoom.getChatId(), pageable);

      // then
      assertThat(messages.getNumberOfElements()).isEqualTo(1);
      assertThat(messages.getContent())
          .containsExactly(alive.getMessageId());
    }
  }

  @Nested
  @DisplayName("커서를 기준으로 이전 페이지 ID를 최신순으로 반환한다")
  class findSliceWithRelationsByChatId {
    /**
     * 마지막 페이지(또는 전체 개수가 size 이하인 케이스) 검증
     */
    private static void assertSlice_theEnd(Slice<Long> messages, int count, int size) {
      // 현재 페이지의 실제 요소 수 = 총 메시지 수 (마지막 페이지라서)
      assertThat(messages.getNumberOfElements()).isEqualTo(count);

      assertThat(messages.getSize()).isEqualTo(size);

      // 마지막 페이지이므로 다음 페이지 없음
      assertThat(messages.hasNext()).isFalse();
    }

    @DisplayName("커서(createdAt, messageId) 이전 메시지 ID를 최신순으로 조회한다")
    @Test
    void findIdSliceByChatIdAndCursor_Success() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      // 시간 간격을 두고 메시지 생성 (정렬 보장)
      LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 0, 0);
      List<Long> messageIds = new ArrayList<>();

      int count = 30;
      for (int i = 0; i < count; i++) {
        Message message = saveMessage(p, "메시지" + (i + 1), baseTime.plusSeconds(i));
        messageIds.add(message.getMessageId());
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      // when
      // 1) 첫 페이지 조회 (커서 없음)
      Slice<Long> firstPage = messageRepository.findIdSliceByChatId(
          chatRoom.getChatId(), pageable);

      // 2) 커서 설정 - 첫 페이지 마지막 메시지
      Long cursorId = firstPage.getContent().get(size - 1);
      Message cursorMessage = messageRepository.findById(cursorId)
          .orElseThrow(() -> new IllegalStateException("커서 메시지를 찾을 수 없습니다"));
      LocalDateTime cursorCreatedAt = cursorMessage.getCreatedAt();

      log.info("커서 정보 - ID: {}, CreatedAt: {}", cursorId, cursorCreatedAt);

      // 3) 두 번째 페이지 조회
      Slice<Long> secondPage = messageRepository.findIdSliceByChatIdAndCursor(
          chatRoom.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      // 실제 저장된 ID를 기반으로 기대값 계산
      List<Long> expectedIds = messageIds.stream()
          .sorted(Comparator.reverseOrder())  // 최신순 정렬
          .skip(size)  // 첫 페이지 건너뛰기
          .limit(size) // 두 번째 페이지 크기만큼
          .collect(Collectors.toList());

      assertThat(secondPage.getContent())
          .hasSize(size)
          .containsExactlyElementsOf(expectedIds)
          .isSortedAccordingTo(Comparator.reverseOrder());

      assertThat(secondPage.hasNext()).isTrue();  // 세 번째 페이지 존재 확인

      // 추가 검증: 커서 메시지는 결과에 포함되지 않음
      assertThat(secondPage.getContent()).doesNotContain(cursorId);
    }

    @DisplayName("메시지가 존재하지 않으면 빈 Slice를 반환한다")
    @Test
    void findIdSliceByChatIdAndCursor_emptyResult() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      saveChatPart(chatRoom, user);

      int count = 0;
      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      Long cursorId = 1L;
      LocalDateTime cursorCreatedAt = LocalDateTime.of(2024, 1, 1, 0, 0);

      // when
      Slice<Long> messages = messageRepository.findIdSliceByChatIdAndCursor(chatRoom.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      assertSlice_theEnd(messages, count, size);
      assertThat(messages.getContent()).isEmpty();
    }

    @DisplayName("이전 메시지 개수가 페이지 크기보다 적을 경우, 모든 메시지를 반환한다")
    @Test
    void findIdSliceByChatIdAndCursor_lessMessageThanSize() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      // 시간 간격을 두고 메시지 생성 (정렬 보장)
      LocalDateTime base = LocalDateTime.of(2024, 1, 1, 0, 0);
      List<Long> messageIds = new ArrayList<>();

      int count = 15;
      for (int i = 0; i < count; i++) {
        Message message = saveMessage(p, "메시지" + (i + 1), base.plusSeconds(i));
        messageIds.add(message.getMessageId());
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      List<Long> expectedIds = messageIds.stream()
          .sorted(Comparator.reverseOrder())  // 최신순 정렬
          .skip(size)  // 첫 페이지 건너뛰기
          .limit(size) // 두 번째 페이지 크기만큼
          .collect(Collectors.toList());

      // when
      // 1) 첫 페이지 조회 (커서 없음)
      Slice<Long> firstPage = messageRepository.findIdSliceByChatId(
          chatRoom.getChatId(), pageable);

      // 2) 커서 설정 - 첫 페이지 마지막 메시지
      Long cursorId = firstPage.getContent().get(size - 1);
      Message cursorMessage = messageRepository.findById(cursorId)
          .orElseThrow(() -> new IllegalStateException("커서 메시지를 찾을 수 없습니다"));
      LocalDateTime cursorCreatedAt = cursorMessage.getCreatedAt();

      log.info("커서 정보 - ID: {}, CreatedAt: {}", cursorId, cursorCreatedAt);

      // 3) 두 번째 페이지 조회
      Slice<Long> secondPage = messageRepository.findIdSliceByChatIdAndCursor(
          chatRoom.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      // 실제 저장된 ID를 기반으로 기대값 계산
      assertThat(secondPage.getContent())
          .hasSize(count - size)
          .containsExactlyElementsOf(expectedIds)
          .isSortedAccordingTo(Comparator.reverseOrder());

      // 추가 검증: 커서 메시지는 결과에 포함되지 않음
      assertThat(secondPage.getContent()).doesNotContain(cursorId);
    }

    @DisplayName("여러 채팅방이 존재해도 조회한 채팅방의 메시지만 반환한다")
    @Test
    void findIdSliceByChatIdAndCursor_ignoreOtherChatRooms() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom1 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p1 = saveChatPart(chatRoom1, user);

      ChatRoom chatRoom2 = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p2 = saveChatPart(chatRoom2, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      // 각 채팅방의 메시지 ID를 저장
      List<Long> chatRoom1Ids = new ArrayList<>();
      int chat1messageNum = 5;
      for (int i = 1; i <= chat1messageNum; i++) {
        Long id = saveMessage(p1, "room1-" + i, base.plusSeconds(i))
            .getMessageId();
        chatRoom1Ids.add(id);
      }

      List<Long> chatRoom2Ids = new ArrayList<>();
      int chat2messageNum = 10;
      for (int i = 1; i <= chat2messageNum; i++) {
        Long id = saveMessage(p2, "room2-" + i, base.plusSeconds(i))
            .getMessageId();
        chatRoom2Ids.add(id);
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      List<Long> allChatRoom1IdsDesc = chatRoom1Ids.stream()
          .sorted(Comparator.reverseOrder())
          .toList();

      // when: 1) 첫 페이지 조회
      Slice<Long> firstPage = messageRepository.findIdSliceByChatId(
          chatRoom1.getChatId(), pageable);

      // 2) 커서 설정 - 첫 페이지 마지막 메시지
      Long cursorId = firstPage.getContent().get(firstPage.getContent().size() - 1);
      Message cursorMessage = messageRepository.findById(cursorId)
          .orElseThrow(() -> new IllegalStateException("커서 메시지를 찾을 수 없습니다"));
      LocalDateTime cursorCreatedAt = cursorMessage.getCreatedAt();

      log.info("커서 정보 - ID: {}, CreatedAt: {}", cursorId, cursorCreatedAt);

      // 3) 커서 이후 페이지 조회
      Slice<Long> secondPage = messageRepository.findIdSliceByChatIdAndCursor(
          chatRoom1.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      // 첫 페이지에 나온 메시지들을 건너뛴 나머지 (두 번째 페이지 기대값)
      List<Long> expectedSecondPageIds = allChatRoom1IdsDesc.stream()
          .skip(firstPage.getNumberOfElements())
          .toList();

      // 두 번째 페이지는 비어있거나 남은 메시지만 포함
      assertThat(secondPage.getContent())
          .hasSize(expectedSecondPageIds.size())
          .containsExactlyElementsOf(expectedSecondPageIds)
          .doesNotContainAnyElementsOf(chatRoom2Ids)  // chatRoom2 메시지는 절대 포함 안 됨
          .doesNotContain(cursorId);  // 커서 메시지는 결과에 포함 안 됨
    }

    @DisplayName("존재하지 않는 채팅방 ID로 조회해도 빈 Slice를 반환한다")
    @Test
    void findIdSliceByChatIdAndCursor_notExistsChatId() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message message = saveMessage(p, "안녕", base);

      // 존재하는 메시지를 커서로 사용
      Long cursorId = message.getMessageId();
      LocalDateTime cursorCreatedAt = message.getCreatedAt();

      Pageable pageable = PageRequest.of(0, 10);

      // when
      // 존재하지 않는 채팅방 ID로 커서 조회
      Slice<Long> messages = messageRepository.findIdSliceByChatIdAndCursor(
          999999L, cursorCreatedAt, cursorId, pageable);

      // then
      assertThat(messages.getContent()).isEmpty();
      assertThat(messages.getNumberOfElements()).isZero();
      assertThat(messages.hasNext()).isFalse();
    }

    @DisplayName("삭제된 메시지는 조회 결과에 포함되지 않는다")
    @Test
    void findIdSliceByChatIdAndCursor_excludeDeletedMessages() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      Message alive = saveMessage(p, "alive", base.plusSeconds(1));
      Message deleted = saveMessage(p, "delete", base.plusSeconds(2));
      Message cursor = saveMessage(p, "cursor", base.plusSeconds(3));

      // 중간 메시지 삭제
      deleted.setDeletedAt(LocalDateTime.now());
      messageRepository.save(deleted);

      // 커서 설정 (가장 최신 메시지)
      Long cursorId = cursor.getMessageId();
      LocalDateTime cursorCreatedAt = cursor.getCreatedAt();

      Pageable pageable = PageRequest.of(0, 10);

      // when - 커서 이전 메시지 조회
      Slice<Long> messages = messageRepository.findIdSliceByChatIdAndCursor(
          chatRoom.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      // 삭제된 메시지는 제외되고 alive만 조회됨
      assertThat(messages.getNumberOfElements()).isEqualTo(1);
      assertThat(messages.getContent())
          .containsExactly(alive.getMessageId())
          .doesNotContain(deleted.getMessageId())
          .doesNotContain(cursorId);  // 커서 메시지도 결과에 포함 안 됨
    }

    @DisplayName("이전 메시지가 없는 경우, 빈 Slice를 반환한다")
    @Test
    void findIdSliceByChatIdAndCursor_noMessageThanPre() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      // 시간 간격을 두고 메시지 생성 (정렬 보장)
      LocalDateTime base = LocalDateTime.of(2024, 1, 1, 0, 0);
      List<Long> messageIds = new ArrayList<>();

      int count = 10;
      for (int i = 0; i < count; i++) {
        Message message = saveMessage(p, "메시지" + (i + 1), base.plusSeconds(i));
        messageIds.add(message.getMessageId());
      }

      int size = 10;
      Pageable pageable = PageRequest.of(0, size);

      // when
      // 1) 첫 페이지 조회 (커서 없음)
      Slice<Long> firstPage = messageRepository.findIdSliceByChatId(
          chatRoom.getChatId(), pageable);

      // 2) 커서 설정 - 첫 페이지 마지막 메시지
      Long cursorId = firstPage.getContent().get(size - 1);
      Message cursorMessage = messageRepository.findById(cursorId)
          .orElseThrow(() -> new IllegalStateException("커서 메시지를 찾을 수 없습니다"));
      LocalDateTime cursorCreatedAt = cursorMessage.getCreatedAt();

      log.info("커서 정보 - ID: {}, CreatedAt: {}", cursorId, cursorCreatedAt);

      // 3) 두 번째 페이지 조회
      Slice<Long> secondPage = messageRepository.findIdSliceByChatIdAndCursor(
          chatRoom.getChatId(), cursorCreatedAt, cursorId, pageable);

      // then
      // 실제 저장된 ID를 기반으로 기대값 계산
      assertThat(secondPage.getContent())
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("id 리스트에 포함되는 모든 메시지 조회")
  class findAllByMessageIds {
    @DisplayName("messageId 리스트로 메시지를 일괄 조회하고 chatPart와 user가 fetch join된다")
    @Test
    void findAllByMessageIds_Success() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      // 각 채팅방의 메시지 ID를 저장
      List<Long> chatRoomIds = new ArrayList<>();
      int chat1messageNum = 5;
      for (int i = 1; i <= chat1messageNum; i++) {
        Long id = saveMessage(p, "message" + i, base.plusSeconds(i))
            .getMessageId();
        chatRoomIds.add(id);
      }

      // 두 채팅방의 메시지 ID를 섞어서 요청 (chatRoom1에서 2개, chatRoom2에서 3개)
      List<Long> requestIds = new ArrayList<>();
      requestIds.add(chatRoomIds.get(0));  // room1의 첫 번째
      requestIds.add(chatRoomIds.get(2));  // room1의 세 번째

      //  요청하지 않은 메시지
      List<Long> notRequestedIds = new ArrayList<>();
      notRequestedIds.addAll(chatRoomIds);
      notRequestedIds.removeAll(requestIds);

      // when
      List<Message> messages = messageRepository.findAllByMessageIds(requestIds);

      // then
      // 1. 요청한 ID 개수만큼 조회됨
      assertThat(messages)
          .hasSize(requestIds.size());

      // 2. 조회된 메시지 ID가 요청한 ID와 정확히 일치
      assertThat(messages)
          .extracting(Message::getMessageId)
          .containsExactlyInAnyOrderElementsOf(requestIds);

      assertThat(messages)
          .extracting(Message::getMessageId)
          .doesNotContainAnyElementsOf(notRequestedIds);

      // 3. fetch join 검증: chatPart와 user가 이미 로딩되어 있어야 함
      for (Message message : messages) {
        assertThat(message.getChatPart()).isNotNull();
        assertThat(message.getChatPart().getUser()).isNotNull();
        assertThat(message.getChatPart().getUser().getUserName()).isEqualTo("user");
      }
    }

    @DisplayName("빈 ID 리스트로 조회하면 빈 리스트를 반환한다")
    @Test
    void findAllByMessageIds_EmptyList() {
      // given
      List<Long> emptyIds = new ArrayList<>();

      // when
      List<Message> messages = messageRepository.findAllByMessageIds(emptyIds);

      // then
      assertThat(messages).isEmpty();
    }

    @DisplayName("존재하지 않는 messageId로 조회하면 빈 리스트를 반환한다")
    @Test
    void findAllByMessageIds_NotExistsIds() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand, ChatRoomType.GROUP);
      ChatPart p = saveChatPart(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      saveMessage(p, "메시지", base);

      List<Long> notExistsIds = List.of(999999L, 888888L);

      // when
      List<Message> messages = messageRepository.findAllByMessageIds(notExistsIds);

      // then
      assertThat(messages).isEmpty();
    }

  }
}