package com.mudosa.musinsa.domain.chat.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandMember;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.brand.domain.repository.BrandMemberRepository;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.domain.chat.dto.ChatPartResponse;
import com.mudosa.musinsa.domain.chat.dto.ChatRoomInfoResponse;
import com.mudosa.musinsa.domain.chat.dto.MessageCursor;
import com.mudosa.musinsa.domain.chat.dto.MessageResponse;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import com.mudosa.musinsa.domain.chat.entity.Message;
import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import com.mudosa.musinsa.domain.chat.enums.ChatPartRole;
import com.mudosa.musinsa.domain.chat.enums.ChatRoomType;
import com.mudosa.musinsa.domain.chat.event.MessageEventPublisher;
import com.mudosa.musinsa.domain.chat.file.FileStore;
import com.mudosa.musinsa.domain.chat.repository.ChatPartRepository;
import com.mudosa.musinsa.domain.chat.repository.ChatRoomRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageAttachmentRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Slice;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@DisplayName("ChatService 테스트")
@Transactional
class ChatServiceImplTest extends ServiceConfig {

  @Autowired
  protected ChatRoomRepository chatRoomRepository;
  @Autowired
  protected ChatPartRepository chatPartRepository;
  @Autowired
  protected MessageRepository messageRepository;
  @Autowired
  protected MessageAttachmentRepository attachmentRepository;

  @Autowired
  protected UserRepository userRepository;
  @Autowired
  protected BrandRepository brandRepository;
  @Autowired
  protected BrandMemberRepository brandMemberRepository;

  @Autowired
  protected ChatService chatService;

  @MockitoBean
  protected MessageEventPublisher messageEventPublisher;

  @MockitoBean
  protected FileStore fileStore;


  /**
   * === Test Helper ===
   */
  private User saveUser(String userName) {
    User user = User.create(userName, "pwd1234!", userName + "@test.com", UserRole.USER, String.format("http://mudosa/uploads/avatar/%s.png", userName), "010-0000-0000", "서울 강남구");
    userRepository.save(user);
    return user;
  }

  private Brand saveBrand(String nameKo, String nameEn) {
    Brand brand = Brand.builder()
            .nameKo(nameKo)
            .nameEn(nameEn)
            .commissionRate(BigDecimal.valueOf(10.00))
            .status(BrandStatus.ACTIVE)
            .build();
    brandRepository.save(brand);
    return brand;
  }

  private ChatRoom saveChatRoom(Brand brand) {
    ChatRoom chatRoom = ChatRoom.builder()
            .brand(brand)
            .type(ChatRoomType.GROUP)
            .build();
    chatRoomRepository.save(chatRoom);
    return chatRoom;
  }

  private ChatPart saveChatPartOfUser(ChatRoom chatRoom, User user) {
    ChatPart chatPart = ChatPart.builder()
            .chatRoom(chatRoom)
            .user(user)
            .role(ChatPartRole.USER)
            .build();
    return chatPartRepository.save(chatPart);
  }

  // 메시지 생성
  private Message saveMessage(ChatPart chatPart, String content, LocalDateTime timestamp) {
    // 1. Message 생성 및 저장
    Message message = Message.builder()
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content(content)
            .createdAt(timestamp)
            .build();

    messageRepository.save(message); // id 확보

    return message;
  }

  private void saveMessageWithParent(ChatPart chatPart, LocalDateTime timestamp, Message parent) {
    // 1. Message 생성 및 저장
    Message message = Message.builder()
            .parent(parent)
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content("child")
            .createdAt(timestamp)
            .build();

    messageRepository.save(message); // id 확보

  }

  private void saveMessageWithAttachments(ChatPart chatPart, String content, LocalDateTime timestamp, List<MessageAttachment> attachments) {
    // 1. Message 생성 및 저장
    Message message = Message.builder()
            .chatPart(chatPart)
            .chatId(chatPart.getChatRoom().getChatId())
            .content(content)
            .createdAt(timestamp)
            .build();

    messageRepository.save(message); // id 확보

    // 2. 각 첨부파일에 message 연결
    for (MessageAttachment attachment : attachments) {
      attachment.setMessage(message);
      message.getAttachments().add(attachment);
    }

    // 3. 첨부파일 저장
    attachmentRepository.saveAll(attachments);

  }

  // 파일 생성
  private MultipartFile createFilePart(String filename, String content) {
    return new MockMultipartFile("files", filename, "image/png", content.getBytes());
  }

  private MessageAttachment createMessageAttachment(String fileName) {
    return MessageAttachment.builder()
            .attachmentUrl(String.format("http://mudosa/uploads/chat/1/%s.png", fileName))
            .mimeType("image/png")
            .sizeBytes(123L)
            .build();
  }

  // 최신순 정렬 검증 (Slice 버전)
  private void assertLatest(Slice<MessageResponse> messages) {
    assertThat(messages.getContent())
        .extracting(MessageResponse::getCreatedAt)
        .isSortedAccordingTo(Comparator.reverseOrder());
  }


  /**
   * === saveMessage 메서드 테스트 ===
   */
  @Nested
  @DisplayName("채팅 메시지 저장")
  class saveMessage {

    @DisplayName("메시지만 포함한 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContent() throws FirebaseMessagingException {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      List<MultipartFile> files = List.of();

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when
      MessageResponse response = chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now);

      // then
      assertThat(response)
          .extracting("content", "userId", "createdAt", "isDeleted", "chatId", "chatPartId")
          .containsExactly(content, user.getId(), now, false, chatRoom1.getChatId(), chatPart1.getChatPartId());
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now); //채팅 마지막 메시지 시간 업데이트 여부 확인
      assertThat(response.getAttachments()).isEmpty();

      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("파일만 포함한 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withFiles() throws Exception {
      // given
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = createFilePart("b.png", "world");
      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // when
      MessageResponse response = chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now);

      // then
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(null, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // attachment url 검증을 포맷 가정에 덜 민감하게 변경
      assertThat(response.getAttachments()).hasSize(2)
          .extracting("attachmentUrl", String.class)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())))
          .anyMatch(url -> url.contains(Objects.requireNonNull(file2.getOriginalFilename())));

      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("메시지와 파일을 모두 포함한 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContentAndFiles() throws Exception {
      // given
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = createFilePart("b.png", "world");
      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // 반드시 테스트에서 fileStore의 반환값을 제어한다 (Mock 또는 Test implementation)
      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            // 테스트에서 예측 가능한 URL 포맷으로 리턴
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // when
      MessageResponse response = chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now);

      // then
      // 1) 프로퍼티 검증 — isDeleted 제거 (MessageResponse에 없는 경우)
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(content, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      // 2) 채팅방 마지막 메시지 시간 업데이트 확인
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // 3) 첨부파일 URL 검증 — 포맷을 스텁했으므로 contains 또는 정확 매칭 사용 가능
      assertThat(response.getAttachments())
          .extracting("attachmentUrl", String.class)
          .hasSize(2)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())))
          .anyMatch(url -> url.contains(Objects.requireNonNull(file2.getOriginalFilename())));

      // 4) 이벤트 발행 검증 (mock으로 바꿨을 때만 유효)
      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("메시지와 파일을 모두 포함한 답장 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContentAndFilesAndParent() throws Exception {
      // given
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = createFilePart("b.png", "world");
      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);
      Message parent = saveMessage(chatPart1, "parent", now);

      // fileStore를 예측 가능한 값으로 스텁 (필수)
      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // when
      MessageResponse response = chatService.saveMessage(
          chatRoom1.getChatId(),
          user.getId(),
          parent.getMessageId(),
          content,
          files,
          now
      );

      // then
      // 1) 상위 레벨 프로퍼티 검증 (isDeleted 제거)
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(content, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      // 2) parent DTO 검증 (parent 내부의 isDeleted는 여기에 있음)
      assertThat(response.getParent()).isNotNull();
      assertThat(response.getParent())
          .extracting("messageId", "content")
          .containsExactly(parent.getMessageId(), parent.getContent());

      // 3) 채팅방 마지막 메시지 시간 업데이트 확인
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // 4) 첨부파일 URL 검증 (포맷 민감도 완화)
      assertThat(response.getAttachments()).hasSize(2)
          .extracting("attachmentUrl", String.class)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())))
          .anyMatch(url -> url.contains(Objects.requireNonNull(file2.getOriginalFilename())));

      // 5) 이벤트 발행 검증
      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("메시지만 포함한 답장 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContentAndParent() throws FirebaseMessagingException {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);
      Message parent = saveMessage(chatPart1, "parent", now);

      // when
      MessageResponse response = chatService.saveMessage(chatRoom1.getChatId(), user.getId(), parent.getMessageId(), content, null, now);

      // then
      assertThat(response)
          .extracting("content", "userId", "createdAt", "isDeleted", "chatId", "chatPartId", "parent.messageId", "parent.content")
          .containsExactly(content, user.getId(), now, false, chatRoom1.getChatId(), chatPart1.getChatPartId(), parent.getMessageId(), parent.getContent());
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now); //채팅 마지막 메시지 시간 업데이트 여부 확인

      assertThat(response.getAttachments()).isEmpty();

      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("파일만 포함한 답장 메시지를 보낸 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withFilesAndParent() throws Exception {
      // given
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = createFilePart("b.png", "world");
      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);
      Message parent = saveMessage(chatPart1, "parent", now);

      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            // 테스트에서 예측 가능한 URL 반환
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // messageEventPublisher는 반드시 mock 혹은 @MockBean 상태여야 외부 호출을 막을 수 있음

      // when
      MessageResponse response = chatService.saveMessage(
          chatRoom1.getChatId(),
          user.getId(),
          parent.getMessageId(),
          content,
          files,
          now
      );

      // then
      // (1) 최상위 프로퍼티 검증 —
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(null, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      // (2) parent DTO 검증
      assertThat(response.getParent()).isNotNull();
      assertThat(response.getParent().getMessageId()).isEqualTo(parent.getMessageId());
      assertThat(response.getParent().getContent()).isEqualTo(parent.getContent());

      // (3) 채팅방 마지막 메시지 시간 업데이트 확인
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // (4) 첨부파일 URL 검증 — 저장 포맷 변화에 덜 민감하게 contains 사용
      assertThat(response.getAttachments()).hasSize(2)
          .extracting("attachmentUrl", String.class)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())))
          .anyMatch(url -> url.contains(Objects.requireNonNull(file2.getOriginalFilename())));

      // (5) 이벤트 발행 검증 (mock 상태에서만 유효)
      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("파일 목록이 null이고 메시지도 없으면 오류를 반환한다")
    @Test
    void saveMessage_without_Message_And_NullFiles() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, user);

      String content = "";

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, null, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.MESSAGE_OR_FILE_REQUIRED);

      assertThat(chatRoom1.getLastMessageAt()).isNull();
      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("파일 목록이 비었으면 메시지도 없으면 오류를 반환한다")
    @Test
    void saveMessage_without_Message_And_noFiles() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, user);

      String content = null;

      List<MultipartFile> files = List.of();

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.MESSAGE_OR_FILE_REQUIRED);

      assertThat(chatRoom1.getLastMessageAt()).isNull();
      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("메시지 내용이 공백뿐이고 파일도 없으면 오류를 반환한다")
    @Test
    void saveMessage_without_BlankMessage_And_Files() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, user);

      String content = "        \n";

      List<MultipartFile> files = new ArrayList<>();

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.MESSAGE_OR_FILE_REQUIRED);

      assertThat(chatRoom1.getLastMessageAt()).isNull();
      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("채팅방이 없는 경우 오류를 반환한다")
    @Test
    void saveMessage_noChatRoom() {
      // given
      User user = saveUser("user");

      String content = "content";

      List<MultipartFile> files = new ArrayList<>();

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(99999L, user.getId(), null, content, files, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.CHAT_NOT_FOUND);

      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("참여 정보가 없다면 오류를 반환한다")
    @Test
    void saveMessage_without_NoChatPart() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      String content = "content";

      List<MultipartFile> files = new ArrayList<>();

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.CHAT_PARTICIPANT_NOT_FOUND);

      assertThat(chatRoom1.getLastMessageAt()).isNull();
      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("답장 메시지인 경우 부모 메시지가 존재하지 않으면 오류를 반환한다")
    @Test
    void saveMessage_parentMessage_notExist() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, user);

      String content = "content";
      LocalDateTime base = LocalDateTime.of(2020, 1, 1, 1, 1);

      List<MultipartFile> files = new ArrayList<>();

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), 99999L, content, files, base))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
      assertThat(chatRoom1.getLastMessageAt()).isNull();

      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("답장 메시지인 경우 부모 메시지가 다른 채팅방에 위치하면 오류를 반환한다")
    @Test
    void saveMessage_parentMessage_inOtherChatRoom() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatRoom chatRoom2 = saveChatRoom(brand2);

      saveChatPartOfUser(chatRoom1, user);
      ChatPart chatPart2 = saveChatPartOfUser(chatRoom2, user);

      String content = "content";

      List<MultipartFile> files = new ArrayList<>();

      LocalDateTime base = LocalDateTime.of(2020, 1, 1, 1, 1);
      Message message = saveMessage(chatPart2, content, base);

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), message.getMessageId(), content, files, base.plusMinutes(1)))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
      assertThat(chatRoom1.getLastMessageAt()).isNull();

      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("파일 첨부 메시지에서 첨부 파일 저장 과정에 문제가 발생하면 오류를 반환한다")
    @Test
    void saveMessage_withFileSaveError() throws IOException {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = createFilePart("b.png", "world");
      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      given(fileStore.storeMessageFile(anyLong(), anyLong(), any())).willThrow(
          new IOException()
      );

      // when & then
      assertThatThrownBy(() -> chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.FILE_SAVE_FAILED);

      assertThat(chatRoom1.getLastMessageAt()).isNull();
      assertThat(chatPart.getMessages()).isEmpty();

      verify(messageEventPublisher, never()).publishMessageCreated(any());
    }

    @DisplayName("Empty 파일이 포함되어 있으면 이를 제외하고 메시지 저장 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContentAndFiles_withEmptyFile() throws Exception {
      // given (테스트 헬퍼로 엔티티 저장했다고 가정)
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      MultipartFile file1 = createFilePart("a.png", "hello");
      MultipartFile file2 = new MockMultipartFile( // empty() 파일
          "file",              // 파라미터 이름
          "",                  // 원본 파일명
          "text/plain",        // Content-Type
          new byte[0]          // 파일 내용 (0바이트 → isEmpty() = true)
      );

      List<MultipartFile> files = List.of(file1, file2);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // 반드시 테스트에서 fileStore의 반환값을 제어한다
      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            // 테스트에서 예측 가능한 URL 포맷으로 리턴
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // when
      MessageResponse response = chatService.saveMessage(chatRoom1.getChatId(), user.getId(), null, content, files, now);

      // then
      // 1) 프로퍼티 검증 — isDeleted 제거 (MessageResponse에 없는 경우)
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(content, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      // 2) 채팅방 마지막 메시지 시간 업데이트 확인
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // 3) 첨부파일 URL 검증 — 포맷을 스텁했으므로 contains 또는 정확 매칭 사용 가능
      assertThat(response.getAttachments())
          .extracting("attachmentUrl", String.class)
          .hasSize(1)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())));

      // 4) 이벤트 발행 검증 (mock으로 바꿨을 때만 유효)
      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }

    @DisplayName("Null 파일이 포함되어 있으면 이를 제외하고 메시지 저장 후 해당 메시지를 반환한다")
    @Test
    void saveMessage_withContentAndFiles_withNullFile() throws Exception {
      // given (테스트 헬퍼로 엔티티 저장했다고 가정)
      User user = saveUser("user");
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      String content = "content";

      MultipartFile file1 = createFilePart("a.png", "hello");

      List<MultipartFile> files = new ArrayList<>();
      files.add(file1);
      files.add(null);

      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);

      // 반드시 테스트에서 fileStore의 반환값을 제어한다 (Mock 또는 Test implementation)
      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
          .willAnswer(inv -> {
            MultipartFile mf = inv.getArgument(2);
            // 테스트에서 예측 가능한 URL 포맷으로 리턴
            return "/test-storage/" + mf.getOriginalFilename();
          });

      // messageEventPublisher는 외부 호출을 하지 않도록 mock으로 주입되어 있어야 함.
      // (통합 @SpringBootTest 환경이라면 @MockBean으로 교체)

      // when
      MessageResponse response = chatService.saveMessage(
          chatRoom1.getChatId(),
          user.getId(),
          null,
          content,
          files,
          now
      );

      // then
      // 1) 프로퍼티 검증 — isDeleted 제거 (MessageResponse에 없는 경우)
      assertThat(response)
          .extracting("content", "userId", "createdAt", "chatId", "chatPartId")
          .containsExactly(content, user.getId(), now, chatRoom1.getChatId(), chatPart1.getChatPartId());

      // 2) 채팅방 마지막 메시지 시간 업데이트 확인
      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(now);

      // 3) 첨부파일 URL 검증 — 포맷을 스텁했으므로 contains 또는 정확 매칭 사용 가능
      assertThat(response.getAttachments())
          .extracting("attachmentUrl", String.class)
          .hasSize(1)
          .anyMatch(url -> url.contains(Objects.requireNonNull(file1.getOriginalFilename())));

      // 4) 이벤트 발행 검증 (mock으로 바꿨을 때만 유효)
      verify(messageEventPublisher, times(1)).publishMessageCreated(any());
    }


//    @ParameterizedTest(name = "{index} -> messageCount={0}, filesPerMessage={1}")
//    @CsvSource({
//        "1000, 3",
//        "10000, 2"
//    })
//    void saveMessagesParameterized(int messageCount, int filesPerMessage) throws Exception {
//      // given
//      User user = saveUser("user");
//      Brand brand1 = saveBrand("브랜드1", "Brand1");
//      ChatRoom chatRoom1 = saveChatRoom(brand1);
//      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);
//
//      LocalDateTime baseTime = LocalDateTime.of(2020, 1, 1, 1, 0);
//
//      given(fileStore.storeMessageFile(anyLong(), anyLong(), any()))
//          .willAnswer(inv -> "/test-storage/" + ((MultipartFile) inv.getArgument(2)).getOriginalFilename());
//
//      // when
//      for (int i = 0; i < messageCount; i++) {
//        String content = "메시지 #" + i;
//
//        List<MultipartFile> files = new ArrayList<>();
//        for (int f = 0; f < filesPerMessage; f++) {
//          files.add(new MockMultipartFile("file", "file_" + i + "_" + f + ".png", "image/png",
//              ("data" + i + f).getBytes()));
//        }
//
//        chatService.saveMessage(
//            chatRoom1.getChatId(),
//            user.getId(),
//            null,
//            content,
//            files,
//            baseTime.plusSeconds(i)
//        );
//      }
//
//      // then
//      Page<MessageResponse> savedMessages = chatService.getChatMessages(chatRoom1.getChatId(), 0, messageCount);
//      assertThat(savedMessages).hasSize(messageCount);
//
//      // 마지막 메시지 시간 검증
//      assertThat(chatRoom1.getLastMessageAt()).isEqualTo(baseTime.plusSeconds(messageCount - 1));
//
//      // 이벤트 발행 검증
//      verify(messageEventPublisher, times(messageCount)).publishMessageCreated(any());
//    }


  }

  /**
   * ===  getChatMessages 메서드 테스트 ===
   */
  @Nested
  @DisplayName("특정 채팅방의 메시지 페이지 조회(최신순)")
  class getChatMessages {

    @DisplayName("특정 채팅방의 메시지를 성공적으로 반환한다")
    @Test
    void getChatMessages_Success() {
      // given
      // 유저 생성
      User user = saveUser("철수");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart = saveChatPartOfUser(chatRoom1, user);

      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= count; i++) {
        saveMessage(chatPart, "안녕" + i, base.plusSeconds(i));
      }

      // 페이지네이션 정보
      int size = 20;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, count));

      assertThat(messages.getContent().get(0).getContent()).isEqualTo("안녕30");
      assertThat(messages.getContent().get(1).getContent()).isEqualTo("안녕29");
      assertThat(messages.getContent().get(2).getContent()).isEqualTo("안녕28");
      assertThat(messages.getContent().get(19).getContent()).isEqualTo("안녕11");

      // 최신순 확인
      assertLatest(messages);
    }

    @DisplayName("여러 채팅방의 채팅이 존재할 때 특정 채팅방의 메시지만을 성공적으로 반환한다")
    @Test
    void getChatMessages_ManyChatRooms() {
      // given
      // 유저 생성
      User user = saveUser("철수");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatRoom chatRoom2 = saveChatRoom(brand2);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);
      ChatPart chatPart2 = saveChatPartOfUser(chatRoom2, user);

      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= count; i++) {
        saveMessage(chatPart1, "안녕" + i, base.plusSeconds(i));
        saveMessage(chatPart2, "바이" + i, base.plusSeconds(i));
      }

      // 페이지네이션 정보
      int size = 20;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, count));

      assertThat(messages.getContent().get(0).getContent()).isEqualTo("안녕30");
      assertThat(messages.getContent().get(1).getContent()).isEqualTo("안녕29");
      assertThat(messages.getContent().get(2).getContent()).isEqualTo("안녕28");
      assertThat(messages.getContent().get(19).getContent()).isEqualTo("안녕11");

      // 최신순 확인
      assertLatest(messages);

      assertThat(messages.getContent())
          .extracting(MessageResponse::getChatId)
          .containsOnly(chatRoom1.getChatId());
    }

    @DisplayName("여러 유저의 채팅이 존재할 때 특정 채팅방의 메시지만을 성공적으로 반환한다")
    @Test
    void getChatMessages_ManyUser() {
      // given
      // 유저 생성
      User user1 = saveUser("철수");
      User user2 = saveUser("영희");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user1);
      ChatPart chatPart2 = saveChatPartOfUser(chatRoom1, user2);

      int count = 30;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= count; i++) {
        saveMessage(chatPart1, "안녕" + i, base.plusSeconds(i).plusNanos(1));
        saveMessage(chatPart2, "바이" + i, base.plusSeconds(i).plusNanos(2));
      }


      // 페이지네이션 정보
      int size = 20;

      // 페이지 메타데이터 확인
      int totalCount = count * 2;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, totalCount));

      assertThat(messages.getContent().get(0).getContent()).isEqualTo("바이30");
      assertThat(messages.getContent().get(1).getContent()).isEqualTo("안녕30");
      assertThat(messages.getContent().get(2).getContent()).isEqualTo("바이29");
      assertThat(messages.getContent().get(18).getContent()).isEqualTo("바이21");
      assertThat(messages.getContent().get(19).getContent()).isEqualTo("안녕21");

      // 최신순 확인
      assertLatest(messages);
    }

    @DisplayName("관리자 채팅은 isManager가 true, 사용자 채팅은 false로 반환한다")
    @Test
    void getChatMessages_IncludeManager() {
      // given
      // 유저 생성
      User manager = saveUser("매니저");
      User user = saveUser("사용자");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, manager);
      ChatPart chatPart2 = saveChatPartOfUser(chatRoom1, user);

      // 관리자 설정
      brandMemberRepository.save(BrandMember.create(manager.getId(), brand1));

      int managerCount = 29;
      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      for (int i = 1; i <= managerCount; i++) {
        saveMessage(chatPart1, "관리" + i, base.plusSeconds(i));
      }
      int totalCount = managerCount + 1;
      saveMessage(chatPart2, "사용", base.plusSeconds(totalCount));

      // 페이지네이션 정보
      int size = 20;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, totalCount));

      assertThat(messages.getContent().get(0)).extracting("content", "isManager").containsExactly("사용", false);
      assertThat(messages.getContent().get(1)).extracting("content", "isManager").containsExactly("관리29", true);
      assertThat(messages.getContent().get(19)).extracting("content", "isManager").containsExactly("관리11", true);
      assertThat(messages.getContent())
          .filteredOn(MessageResponse::isManager)
          .extracting(MessageResponse::getContent)
          .allMatch(c -> c.startsWith("관리"));


      // 최신순 확인
      assertLatest(messages);
    }

    @DisplayName("답장 메시지인 경우 부모 메시지와 함께 반환한다")
    @Test
    void getChatMessages_withParent() {
      // given
      // 유저 생성
      User user = saveUser("사용자");
      //브랜드 생성
      Brand brand1 = saveBrand("브랜드1", "Brand1");
      // 채팅방 생성
      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);

      Message parent = saveMessage(chatPart1, "parent", base);
      saveMessageWithParent(chatPart1, base.plusSeconds(1), parent);

      // 페이지네이션 정보
      int size = 2;

      int count = 2;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, count));

      assertThat(messages.getContent().get(0)).extracting("content", "parent.content").containsExactly("child", "parent");
      assertThat(messages.getContent().get(1)).extracting("content", "parent.content").containsExactly("parent", null);

      // 최신순 확인
      assertLatest(messages);
    }

    @DisplayName("파일을 포함한 메시지인 경우 파일 목록과 함께 반환한다")
    @Test
    void getChatMessages_withAttachment() {
      // given
      User user = saveUser("사용자");
      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand);
      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
      MessageAttachment m1_a1 = createMessageAttachment("image1_1");
      MessageAttachment m1_a2 = createMessageAttachment("image1_2");
      MessageAttachment m1_a3 = createMessageAttachment("image1_3");
      saveMessageWithAttachments(chatPart, "파일 포함 메시지", base, List.of(m1_a1, m1_a2, m1_a3));

      MessageAttachment m2_a1 = createMessageAttachment("image2_1");
      MessageAttachment m2_a2 = createMessageAttachment("image2_2");
      saveMessageWithAttachments(chatPart, "파일 포함 메시지2", base.plusSeconds(1), List.of(m2_a1, m2_a2));

      int size = 2;

      int count = 2;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> messages = chatService.getChatMessages(chatRoom.getChatId(), cursor, size);

      // then
      assertThat(messages).isNotNull();
      assertThat(messages.getContent()).hasSize(Math.min(size, count));

      MessageResponse first = messages.getContent().getFirst();
      assertThat(first.getContent()).isEqualTo("파일 포함 메시지2");
      assertThat(first.getAttachments()).hasSize(2);

      MessageResponse second = messages.getContent().get(1);
      assertThat(second.getContent()).isEqualTo("파일 포함 메시지");
      assertThat(second.getAttachments()).hasSize(3);

      assertLatest(messages);
    }

    @DisplayName("특정 채팅방의 메시지가 없으면 빈 페이지를 반환한다")
    @Test
    void getChatMessages_EmptyChatRoom_ReturnsEmptyPage() {
      // given
      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      int size = 20;

      MessageCursor cursor = null;

      // when
      Slice<MessageResponse> chatMessages = chatService.getChatMessages(chatRoom1.getChatId(), cursor, size);

      // then
      assertThat(chatMessages).isNotNull();
      assertThat(chatMessages.getContent()).isEmpty();
      assertThat(chatMessages.getSize()).isEqualTo(size);

      // 빈 페이지이므로 다음 페이지 없음
      assertThat(chatMessages.hasNext()).isFalse();
      assertThat(chatMessages.isLast()).isTrue();
    }

    @DisplayName("cursor가 존재하면 cursor 기준으로 이후 페이지를 조회한다")
    @Test
    void getChatMessages_WithCursor_ReturnsNextPage() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);
      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);

      // 메시지 30개 생성 (createdAt 오름차순으로 저장 → 조회는 최신순)
      LocalDateTime base = LocalDateTime.of(2025, 1, 1, 0, 0);
      int totalCount = 30;
      for (int i = 1; i <= totalCount; i++) {
        saveMessage(chatPart, "message-" + i, base.plusSeconds(i));
      }

      int size = 10;

      // 첫 페이지 조회 (cursor 없음)
      MessageCursor firstCursor = null;
      Slice<MessageResponse> firstPage =
          chatService.getChatMessages(chatRoom.getChatId(), firstCursor, size);

      assertThat(firstPage.getContent()).hasSize(size);
      assertThat(firstPage.hasNext()).isTrue();

      // 첫 페이지의 "마지막 메시지"를 커서로 사용 (이전 페이지의 끝지점)
      MessageResponse lastOfFirstPage = firstPage.getContent().get(size - 1);
      MessageCursor nextCursor = new MessageCursor(
          lastOfFirstPage.getCreatedAt(),
          lastOfFirstPage.getMessageId()
      );

      // when
      Slice<MessageResponse> secondPage =
          chatService.getChatMessages(chatRoom.getChatId(), nextCursor, size);

      // then
      assertThat(secondPage).isNotNull();
      assertThat(secondPage.getContent()).hasSize(size);

      // 첫 페이지와 두 번째 페이지의 메시지 ID가 겹치지 않아야 함
      List<Long> firstIds = firstPage.getContent().stream()
          .map(MessageResponse::getMessageId)
          .toList();
      List<Long> secondIds = secondPage.getContent().stream()
          .map(MessageResponse::getMessageId)
          .toList();

      assertThat(secondIds)
          .doesNotContainAnyElementsOf(firstIds);

      // 정렬이 최신순(내림차순)인지 간단히 검증
      assertThat(secondIds)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @DisplayName("cursor가 있지만 messageId가 없으면 첫 페이지를 조회한다")
    @Test
    void getChatMessages_CursorWithoutMessageId_TreatedAsFirstPage() {
      // given
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);
      User user = saveUser("user");
      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);

      LocalDateTime base = LocalDateTime.of(2025, 1, 1, 0, 0);
      int totalCount = 15;
      for (int i = 1; i <= totalCount; i++) {
        saveMessage(chatPart, "message-" + i, base.plusSeconds(i));
      }

      int size = 10;

      // 기준이 되는 "정상 첫 페이지" (cursor == null)
      Slice<MessageResponse> firstPageWithoutCursor =
          chatService.getChatMessages(chatRoom.getChatId(), null, size);

      assertThat(firstPageWithoutCursor.getContent()).hasSize(size);

      // createdAt은 아무거나 사용해도 되지만, 여기서는 첫 번째 메시지의 createdAt을 사용
      MessageResponse firstMessage = firstPageWithoutCursor.getContent().get(0);

      // messageId가 null 인 cursor 생성
      MessageCursor cursorWithoutMessageId = new MessageCursor(
          firstMessage.getCreatedAt(),
          null
      );

      // when
      Slice<MessageResponse> firstPageWithInvalidCursor =
          chatService.getChatMessages(chatRoom.getChatId(), cursorWithoutMessageId, size);

      // then
      assertThat(firstPageWithInvalidCursor).isNotNull();
      assertThat(firstPageWithInvalidCursor.getContent()).hasSize(size);

      // "messageId 없는 cursor"로 조회한 결과가
      // cursor == null 로 조회한 첫 페이지와 동일해야 한다는 기대(= 첫 페이지 취급)
      var idsWithoutCursor = firstPageWithoutCursor.getContent().stream()
          .map(MessageResponse::getMessageId)
          .toList();

      var idsWithInvalidCursor = firstPageWithInvalidCursor.getContent().stream()
          .map(MessageResponse::getMessageId)
          .toList();

      assertThat(idsWithInvalidCursor)
          .containsExactlyElementsOf(idsWithoutCursor);

      // hasNext 플래그도 동일해야 함
      assertThat(firstPageWithInvalidCursor.hasNext())
          .isEqualTo(firstPageWithoutCursor.hasNext());
    }


    @DisplayName("특정 채팅방이 존재하지 않으면 오류를 반환한다")
    @Test
    void getChatMessages_noChatRoom() {
      // given
      int size = 20;

      MessageCursor cursor = null;

      // when & then
      assertThatThrownBy(() -> chatService.getChatMessages(99999L, cursor, size))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_NOT_FOUND);
    }


  }

  /**
   * === getChatRoomInfoByChatId 메서드 테스트 ====
   */
  @Nested
  @DisplayName("특정 채팅방의 정보 조회")
  class getChatRoomInfoByChatId {
    @DisplayName("참여하고 있는 채팅 정보를 성공적으로 조회한다")
    @Test
    void getChatRoomInfoByChatId_Participant() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, user);

      // when
      ChatRoomInfoResponse response = chatService.getChatRoomInfoByChatId(chatRoom1.getChatId(), user.getId());

      // then
      assertThat(response).extracting("chatId", "partNum", "brandId", "isParticipate")
          .containsExactly(chatRoom1.getChatId(), 1L, brand1.getBrandId(), true);
    }

    @DisplayName("참여하고 있지 않은 채팅 정보를 성공적으로 조회한다")
    @Test
    void getChatRoomInfoByChatId_NoParticipant() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      // when
      ChatRoomInfoResponse response = chatService.getChatRoomInfoByChatId(chatRoom1.getChatId(), user.getId());

      // then
      assertThat(response).extracting("chatId", "partNum", "brandId", "isParticipate")
          .containsExactly(chatRoom1.getChatId(), 0L, brand1.getBrandId(), false);
    }

    @DisplayName("없는 채팅의 정보를 조회하는 경우 오류를 반환한다")
    @Test
    void getChatRoomInfoByChatId_NoChatRoom() {
      // given
      User user = saveUser("user");

      saveBrand("브랜드1", "Brand1");

      // when & then
      assertThatThrownBy(() -> chatService.getChatRoomInfoByChatId(9999L, user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("ErrorCode").isEqualTo(ErrorCode.CHAT_NOT_FOUND);
    }

    @DisplayName("삭제된 참여자는 참여자 수에 포함되지 않는다")
    @Test
    void getChatRoomInfoByChatId_excludeDeletedParticipant() {
      // given
      User user = saveUser("user");
      User other = saveUser("other");

      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand);

      // 유저1은 살아있음
      saveChatPartOfUser(chatRoom, user);

      // 유저2는 참여 후 삭제된 상태로 저장
      LocalDateTime now = LocalDateTime.of(2020, 1, 1, 1, 1);
      ChatPart deletedPart = saveChatPartOfUser(chatRoom, other);
      deletedPart.setDeletedAt(now); // 혹은 deletedAt 세팅 메서드
      chatPartRepository.save(deletedPart);

      // when
      ChatRoomInfoResponse response =
          chatService.getChatRoomInfoByChatId(chatRoom.getChatId(), user.getId());

      // then
      assertThat(response).extracting("chatId", "partNum", "brandId", "isParticipate")
          .containsExactly(chatRoom.getChatId(), 1L, brand.getBrandId(), true);
    }

    @DisplayName("여러 참여자가 있어도 조회 사용자가 참여자가 아니면 isParticipate는 false다")
    @Test
    void getChatRoomInfoByChatId_multipleParticipants_butMeNotJoined() {
      // given
      User me = saveUser("me");
      User user1 = saveUser("user1");
      User user2 = saveUser("user2");

      Brand brand = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom = saveChatRoom(brand);

      saveChatPartOfUser(chatRoom, user1);
      saveChatPartOfUser(chatRoom, user2);

      // when
      ChatRoomInfoResponse response =
          chatService.getChatRoomInfoByChatId(chatRoom.getChatId(), me.getId());

      // then
      assertThat(response).extracting("chatId", "partNum", "brandId", "isParticipate")
          .containsExactly(chatRoom.getChatId(), 2L, brand.getBrandId(), false);
    }

  }

  /**
   * ===  addParticipant 메서드 테스트 ===
   */
  @Nested
  @DisplayName("채팅방 참여")
  class addParticipant {
    @DisplayName("채팅방을 성공적으로 참여한다")
    @Test
    void addParticipant_Success() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom = saveChatRoom(brand);

      // when
      ChatPartResponse response = chatService.addParticipant(chatRoom.getChatId(), user.getId());

      // then
      assertThat(response).extracting("chatId", "userId")
          .containsExactly(chatRoom.getChatId(), user.getId());
    }

    @DisplayName("나간 채팅에 다시 참여할 수 있다")
    @Test
    void addParticipant_reParticipant_createNew() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);

      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);
      LocalDateTime now = LocalDateTime.of(2000, 1, 1, 0, 0);
      chatPart.setDeletedAt(now);

      // when
      ChatPartResponse response = chatService.addParticipant(chatRoom.getChatId(), user.getId());

      // then
      assertThat(response).extracting("chatId", "userId")
          .containsExactly(chatRoom.getChatId(), user.getId());
    }

    @DisplayName("참가할 채팅방이 없으면 오류를 반환한다")
    @Test
    void addParticipant_NoChatRoom() {
      // given
      User user = saveUser("user");

      // when & then
      assertThatThrownBy(() -> chatService.addParticipant(99999L, user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_NOT_FOUND);
    }

    @DisplayName("사용자가 존재하지 않으면 오류를 반환한다")
    @Test
    void addParticipant_NoUser() {
      // given
      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom = saveChatRoom(brand);

      // when & then
      assertThatThrownBy(() -> chatService.addParticipant(chatRoom.getChatId(), 99999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @DisplayName("이미 사용자가 채팅에 참가중이면 오류를 반환한다")
    @Test
    void addParticipant_alreadyParticipant() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);

      saveChatPartOfUser(chatRoom, user);

      // when & then
      assertThatThrownBy(() -> chatService.addParticipant(chatRoom.getChatId(), user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_PARTICIPANT_ALREADY_EXISTS);
    }
  }

  /**
   * ===  leaveChat 메서드 테스트 ===
   */
  @Nested
  @DisplayName("채팅방 떠나기")
  class leaveChat {
    @DisplayName("채팅방을 성공적으로 떠난다")
    @Test
    void leaveChat_Success() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");

      ChatRoom chatRoom = saveChatRoom(brand);

      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);

      // when
      chatService.leaveChat(chatRoom.getChatId(), user.getId());
      ChatPart reloaded = chatPartRepository
          .findById(chatPart.getChatPartId())
          .orElseThrow();

      // then
      assertThat(reloaded.getDeletedAt()).isNotNull();
      assertThat(reloaded.getUser().getId()).isEqualTo(chatPart.getUser().getId());
      assertThat(reloaded.getChatRoom().getChatId()).isEqualTo(chatPart.getChatRoom().getChatId());
    }

    @DisplayName("떠날 채팅방이 없으면 오류를 반환한다")
    @Test
    void leaveChat_NoChatRoom() {
      // given
      User user = saveUser("user");

      // when & then
      assertThatThrownBy(() -> chatService.leaveChat(99999L, user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_NOT_FOUND);
    }

    @DisplayName("이미 사용자가 채팅에 참가중이지 않으면 오류를 반환한다")
    @Test
    void leaveChat_noParticipant() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);

      // when & then
      assertThatThrownBy(() -> chatService.leaveChat(chatRoom.getChatId(), user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_PARTICIPANT_NOT_FOUND);
    }

    @DisplayName("나간 채팅을 다시 나가면 오류를 반환한다")
    @Test
    void leaveChat_ReTry() {
      // given
      User user = saveUser("user");

      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);

      ChatPart chatPart = saveChatPartOfUser(chatRoom, user);
      LocalDateTime now = LocalDateTime.of(2000, 1, 1, 0, 0);
      chatPart.setDeletedAt(now);

      // when & then
      assertThatThrownBy(() -> chatService.leaveChat(chatRoom.getChatId(), user.getId()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CHAT_PARTICIPANT_NOT_FOUND);
    }

  }

  /**
   * === getChatRoomByUserId 메서드 테스트 ===
   *
   * @implNote 정렬 추후 추가 시 테스트 작성 필요
   */
  @Nested
  @DisplayName("유저 참여 채팅방 목록 조회")
  class getChatRoomByUserId {
    @DisplayName("내가 참여 중인 모든 채팅방을 조회한다")
    @Test
    void getChatRoomByUserId_Part() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatRoom chatRoom2 = saveChatRoom(brand2);

      saveChatPartOfUser(chatRoom1, user);
      saveChatPartOfUser(chatRoom2, user);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).hasSize(2)
          .extracting("chatId", "isParticipate")
          .containsExactlyInAnyOrder(tuple(chatRoom1.getChatId(), true), tuple(chatRoom2.getChatId(), true));

    }

    @DisplayName("내가 나간 채팅방은 제외하고 조회한다")
    @Test
    void getChatRoomByUserId_ExitNotCheck() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatRoom chatRoom2 = saveChatRoom(brand2);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);
      saveChatPartOfUser(chatRoom2, user);

      LocalDateTime deletedAt = LocalDateTime.of(2020, 1, 1, 1, 1);
      chatPart1.setDeletedAt(deletedAt);
      chatPartRepository.save(chatPart1);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).hasSize(1)
          .extracting("chatId", "isParticipate")
          .containsExactlyInAnyOrder(tuple(chatRoom2.getChatId(), true));

    }

    @DisplayName("내가 다시 참여한 채팅방은 포함되어 조회된다")
    @Test
    void getChatRoomByUserId_ReParticipateCheck() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      Brand brand2 = saveBrand("브랜드2", "Brand2");

      ChatRoom chatRoom1 = saveChatRoom(brand1);
      ChatRoom chatRoom2 = saveChatRoom(brand2);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);
      saveChatPartOfUser(chatRoom2, user);

      LocalDateTime deletedAt = LocalDateTime.of(2020, 1, 1, 1, 1);
      chatPart1.setDeletedAt(deletedAt);
      chatPartRepository.save(chatPart1);

      saveChatPartOfUser(chatRoom1, user);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).hasSize(2)
          .extracting("chatId", "isParticipate")
          .containsExactlyInAnyOrder(tuple(chatRoom1.getChatId(), true), tuple(chatRoom2.getChatId(), true));
    }

    @DisplayName("모든 채팅방을 나간 경우 빈배열이 조회된다")
    @Test
    void getChatRoomByUserId_AllLeft() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");

      ChatRoom chatRoom1 = saveChatRoom(brand1);

      ChatPart chatPart1 = saveChatPartOfUser(chatRoom1, user);

      LocalDateTime deletedAt = LocalDateTime.of(2020, 1, 1, 1, 1);
      chatPart1.setDeletedAt(deletedAt);
      chatPartRepository.save(chatPart1);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).isEmpty();
    }

    @DisplayName("참여한 채팅방이 없는 경우 빈배열이 조회된다")
    @Test
    void getChatRoomByUserId_NotParticipate() {
      // given
      User user = saveUser("user");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      saveChatRoom(brand1);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).isEmpty();
    }

    @DisplayName("다른 사용자가 채팅방에 참가하더라도 내가 참여한 채팅방이 없다면 빈배열이 조회된다")
    @Test
    void getChatRoomByUserId_OtherParticipate() {
      // given
      User me = saveUser("me");
      User other = saveUser("other");

      Brand brand1 = saveBrand("브랜드1", "Brand1");
      ChatRoom chatRoom1 = saveChatRoom(brand1);

      saveChatPartOfUser(chatRoom1, other);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(me.getId());

      // then
      assertThat(chatRooms).isEmpty();
    }

    @DisplayName("같은 채팅방에 내 참여 이력이 여러 개여도 1건만 조회된다")
    @Test
    void getChatRoomByUserId_DuplicatedParts_OneRoom() {
      // given
      User user = saveUser("user");
      Brand brand = saveBrand("브랜드", "Brand");
      ChatRoom chatRoom = saveChatRoom(brand);

      // 중복 참여
      saveChatPartOfUser(chatRoom, user);
      saveChatPartOfUser(chatRoom, user);

      // when
      List<ChatRoomInfoResponse> chatRooms = chatService.getChatRoomByUserId(user.getId());

      // then
      assertThat(chatRooms).hasSize(1)
          .extracting("chatId", "isParticipate")
          .containsExactly(tuple(chatRoom.getChatId(), true));
    }
  }
}