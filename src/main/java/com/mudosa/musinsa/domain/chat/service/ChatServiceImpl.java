package com.mudosa.musinsa.domain.chat.service;

import com.mudosa.musinsa.brand.domain.repository.BrandMemberRepository;
import com.mudosa.musinsa.domain.chat.dto.*;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.entity.ChatRoom;
import com.mudosa.musinsa.domain.chat.entity.Message;
import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import com.mudosa.musinsa.domain.chat.event.MessageEventPublisher;
import com.mudosa.musinsa.domain.chat.file.FileStore;
import com.mudosa.musinsa.domain.chat.mapper.ChatRoomMapper;
import com.mudosa.musinsa.domain.chat.repository.ChatPartRepository;
import com.mudosa.musinsa.domain.chat.repository.ChatRoomRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageAttachmentRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.notification.domain.event.NotificationEventPublisher;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채팅 비즈니스 로직 처리 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatPartRepository chatPartRepository;
  private final MessageRepository messageRepository;
  private final MessageAttachmentRepository attachmentRepository;
  //  @Qualifier("springPublisher") // 추후 누구를 쓸지 지정하고자 하면 추가 필요
  private final ApplicationEventPublisher eventPublisher;
  private final MessageEventPublisher messageEventPublisher;
  private final UserRepository userRepository;
  private final BrandMemberRepository brandMemberRepository;
  private final ChatRoomMapper chatRoomMapper;
  private final NotificationEventPublisher notificationEventPublisher;

  private final FileStore fileStore;

  private final Tracer tracer;

  @Override
  @Transactional(readOnly = true)
  public List<ChatRoomInfoResponse> getChatRoomByUserId(Long userId) {
    //userId, chatId 쌍이 존재하고 delete_at이 null(떠나지 않은 사용자)에 만족하는 채팅방 불러오기
    List<ChatRoom> chatRooms =
        chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(userId);

    //채팅방을 dto list 형태로 변환
    return chatRooms.stream()
        .map(chatRoom -> {
          // 이 시점에서는 유저가 참여 중인 방만 조회했으므로 true 고정
          ChatRoomInfoResponse base = chatRoomMapper.toChatRoomInfoResponse(chatRoom, true);
          return base;
        })
        .toList();
  }

  /**
   * 메시지 저장
   */
  @Override
  @Transactional
  public MessageResponse saveMessage(Long chatId, Long userId, Long parentId, String content, List<MultipartFile> files, LocalDateTime now) {

    //시작 시간으로 고정(여러번 호출시 시간이 달라지는 문제 발생 가능)
    log.debug("[chatId={}][userId={}] 메시지 저장 시작. parentId={}, contentLength={}, fileCount={}",
        chatId, userId, parentId,
        (content != null ? content.length() : 0),
        (files != null ? files.size() : 0));

    // 0) 기본 검증(전송된 파일 & 메시지가 모두 없으면 오류)
    validateMessageOrFiles(content, files);
    log.debug("[chatId={}][userId={}] 메시지/파일 검증 완료", chatId, userId);

    // 1) 채팅방 확인
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);
    log.debug("[chatId={}] 채팅방 검증 완료.", chatId);

    //참여자 정보 확인
    ChatPart chatPart = getChatPartOrThrow(chatId, userId);
    log.debug("[chatId={}][userId={}] 참가 여부 검증 완료.", chatId, userId);

    // 2) 부모 메시지 확인 (같은 방인지까지 확인)
    Message parent = getParentMessageIfExists(parentId, chatId);
    if (parent != null) {
      log.debug("[chatId={}][userId={}] 부모 메시지 존재. parentMessageId={}", chatId, userId, parent.getMessageId());
    }

    // 3) 메시지 엔티티 생성/저장
    Message message = Message.createMessage(content, now, chatPart, parent);

    Message savedMessage = messageRepository.save(message);


    log.info("[chatId={}][userId={}] 메시지 저장 완료. messageId={}", chatId, userId, savedMessage.getMessageId());

    // 4) 첨부파일 저장
    List<MessageAttachment> savedAttachments = saveAttachments(chatId, savedMessage.getMessageId(), files, savedMessage);
    log.info("[chatId={}][userId={}] 첨부파일 {}개 저장 완료", chatId, userId, savedAttachments.size());

    // 채팅방 마지막 메시지 시간 갱신
    chatRoom.setLastMessageAt(now);

    // 5) 응답 생성
    MessageResponse dto = MessageResponse.from(savedMessage, savedAttachments);

    // 6) 이벤트 발행 (AFTER_COMMIT 리스너에서 실제 전송)
    publishMessageEvents(dto);

    return dto;
  }

  private Slice<Long> getChatMessagesSlice(Long chatId, MessageCursor cursor, int size) {
    // hasNext 판단 위해 size+1
    Pageable pageable = PageRequest.of(0, size + 1);

    if (cursor == null) {
      return messageRepository.findIdSliceByChatId(chatId, pageable);
    }

    if (cursor.messageId() == null) {
      return messageRepository.findIdSliceByChatId(chatId, pageable);
    }

    return messageRepository.findIdSliceByChatIdAndCursor(chatId, cursor.createdAt(), cursor.messageId(), pageable);
  }

  @Transactional(readOnly = true)
  protected MessagesBundle loadMessages(Long chatId, MessageCursor cursor, int size) {

    // 전체 loadMessages용 span
    Span span = tracer.nextSpan()
        .name("chat.loadMessages")
        .tag("chat.id", String.valueOf(chatId))
        .start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {

      // 1) 채팅방 유효성 검증
      Span roomSpan = tracer.nextSpan()
          .name("chat.loadChatRoom")
          .start();

      ChatRoom chatRoom;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(roomSpan)) {
        chatRoom = getChatRoomOrThrow(chatId);
      } finally {
        roomSpan.end();
      }

      // 2) 메시지 페이지 조회 (keyset)
      Span msgPageSpan = tracer.nextSpan()
          .name("chat.loadMessagesPage")
          .start();

      Slice<Long> idSlice;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(msgPageSpan)) {
        idSlice = getChatMessagesSlice(chatId, cursor, size);
      } finally {
        msgPageSpan.end();
      }

      List<Long> ids = idSlice.getContent();
      if (ids.isEmpty()) {
        return MessagesBundle.empty(size);
      }

      boolean hasNext = idSlice.hasNext();

      // over-fetch 했으니까 content에서 앞에 size개만 사용
      List<Long> pageIds = ids.size() > size ? ids.subList(0, size) : ids;

      // 3) 실제 메시지 + sender 로딩
      Span msgEntitySpan = tracer.nextSpan()
          .name("chat.loadMessagesEntities")
          .tag("message.count", String.valueOf(pageIds.size()))
          .start();

      List<Message> messages;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(msgEntitySpan)) {
        messages = messageRepository.findAllByMessageIds(pageIds);
      } finally {
        msgEntitySpan.end();
      }

      // 🔹 DB에서 받은 ID 순서를 그대로 살리기 위해 Map → pageIds 순으로 재조합
      Map<Long, Message> messageMap = messages.stream()
          .collect(Collectors.toMap(Message::getMessageId, Function.identity()));

      List<Message> orderedMessages = pageIds.stream()
          .map(messageMap::get)
          .filter(Objects::nonNull)
          .toList();

      // 🔹 더 이상 정렬 필요 없음 (pageIds는 이미 keyset 정렬 기반)
      // messages.sort(...); 제거


      // 4) 부모 ID 수집 (중복 제거를 Set으로)
      Span parentIdSpan = tracer.nextSpan()
          .name("chat.collectParentIds")
          .start();

      List<Long> parentIds;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(parentIdSpan)) {
        Set<Long> parentIdSet = orderedMessages.stream()
            .map(Message::getParent)
            .filter(Objects::nonNull)
            .map(Message::getMessageId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        parentIds = new ArrayList<>(parentIdSet);
      } finally {
        parentIdSpan.end();
      }

      // 5) 부모 메시지 batch 로딩
      Map<Long, Message> parentMap = Collections.emptyMap();
      if (!parentIds.isEmpty()) {
        Span parentSpan = tracer.nextSpan()
            .name("chat.loadParentMessages")
            .tag("parent.count", String.valueOf(parentIds.size()))
            .start();

        try (Tracer.SpanInScope ignored2 = tracer.withSpan(parentSpan)) {
          List<Message> parentMessages = messageRepository.findAllByMessageIds(parentIds);
          parentMap = parentMessages.stream()
              .collect(Collectors.toMap(Message::getMessageId, Function.identity()));
        } finally {
          parentSpan.end();
        }
      }

      // 6) 브랜드 관리자 ID 조회
      Span mgrSpan = tracer.nextSpan()
          .name("chat.loadBrandManagers")
          .start();

      Set<Long> managerUserIds;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(mgrSpan)) {
        Long brandId = chatRoom.getBrand().getBrandId();
        List<Long> managerIds = brandMemberRepository.findActiveUserIdsByBrandId(brandId);
        managerUserIds = new HashSet<>(managerIds);
      } finally {
        mgrSpan.end();
      }

      // 7) 메시지/부모 ID 합치기 (첨부 조회용) - Set으로 distinct
      Span idCollectSpan = tracer.nextSpan()
          .name("chat.collectAttachmentIds")
          .start();

      List<Long> allIds;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(idCollectSpan)) {

        Set<Long> allIdSet = new LinkedHashSet<>();
        for (Message msg : orderedMessages) {
          allIdSet.add(msg.getMessageId());
        }
        allIdSet.addAll(parentIds);

        allIds = new ArrayList<>(allIdSet);
      } finally {
        idCollectSpan.end();
      }

      // 8) 첨부 조회
      Span attSpan = tracer.nextSpan()
          .name("chat.loadAttachments")
          .tag("message.count", String.valueOf(allIds.size()))
          .start();

      Map<Long, List<AttachmentResponse>> attachmentMap;
      try (Tracer.SpanInScope ignored2 = tracer.withSpan(attSpan)) {
        attachmentMap = attachmentRepository.findAllByMessageIdIn(allIds).stream()
            .collect(Collectors.groupingBy(
                ma -> ma.getMessage().getMessageId(),
                Collectors.mapping(AttachmentResponse::of, Collectors.toList())
            ));
      } finally {
        attSpan.end();
      }

      // 9) 최종 Bundle 구성
      Span bundleSpan = tracer.nextSpan()
          .name("chat.buildMessagesBundle")
          .start();

      try (Tracer.SpanInScope ignored2 = tracer.withSpan(bundleSpan)) {
        return new MessagesBundle(
            orderedMessages,
            hasNext,
            managerUserIds,
            attachmentMap,
            parentMap
        );
      } finally {
        bundleSpan.end();
      }

    } finally {
      span.end();
    }
  }

  @Override
  public Slice<MessageResponse> getChatMessages(Long chatId, MessageCursor cursor, int size) {
    getChatRoomOrThrow(chatId);

    Span span = tracer.nextSpan()
        .name("chat.getChatMessages")
        .tag("chat.id", String.valueOf(chatId))
        .start();

    try (SpanInScope ignored = tracer.withSpan(span)) {

      // DB + 배치 로딩
      MessagesBundle bundle = loadMessages(chatId, cursor, size);

      if (bundle.messages().isEmpty()) {
        return new SliceImpl<>(List.of(), PageRequest.of(0, size), false);
      }

      // DTO 매핑 구간 span
      Span dtoSpan = tracer.nextSpan().name("chat.mapMessagesToDto").start();
      List<MessageResponse> dtoList;
      try (SpanInScope ignored2 = tracer.withSpan(dtoSpan)) {

        List<Message> messages = bundle.messages();
        Set<Long> managerUserIds = bundle.managerUserIds();
        Map<Long, List<AttachmentResponse>> attachmentMap = bundle.attachmentMap();
        Map<Long, Message> parentMap = bundle.parentMap();

        dtoList = messages.stream()
            .map(msg -> {

              // parent 엔티티를 parentMap에서 가져옴
              Message parent = null;
              if (msg.getParent() != null) {
                Long parentId = msg.getParent().getMessageId();
                parent = parentMap.get(parentId);
              }

              // 현재 메시지 첨부
              List<AttachmentResponse> currentAttachments =
                  attachmentMap.getOrDefault(msg.getMessageId(), Collections.emptyList());

              // ParentMessageResponse 생성
              ParentMessageResponse parentDto = null;
              if (parent != null) {

                List<AttachmentResponse> parentAttachments =
                    attachmentMap.getOrDefault(parent.getMessageId(), Collections.emptyList());

                var parentCp = parent.getChatPart();
                Long parentUserId = (parentCp != null && parentCp.getUser() != null)
                    ? parentCp.getUser().getId()
                    : null;
                String parentUserName = (parentCp != null && parentCp.getUser() != null)
                    ? parentCp.getUser().getUserName()
                    : "SYSTEM";

                parentDto = ParentMessageResponse.builder()
                        .messageId(parent.getMessageId())
                        .userId(parentUserId)
                        .userName(parentUserName)
                        .content(parent.getContent())
                        .createdAt(parent.getCreatedAt())
                        .attachments(parentAttachments)
                        .build();
              }

              // sender
              var cp = msg.getChatPart();
              Long senderUserId = (cp != null && cp.getUser() != null)
                  ? cp.getUser().getId()
                  : null;
              String senderName = (cp != null && cp.getUser() != null)
                  ? cp.getUser().getUserName()
                  : "SYSTEM";

              boolean isManager = senderUserId != null && managerUserIds.contains(senderUserId);

              return MessageResponse.builder()
                      .messageId(msg.getMessageId())
                      .chatId(msg.getChatId())
                      .chatPartId(cp != null ? cp.getChatPartId() : null)
                      .userId(senderUserId)
                      .userName(senderName)
                      .content(msg.getContent())
                      .attachments(currentAttachments)
                      .createdAt(msg.getCreatedAt())
                      .parent(parentDto)
                      .isManager(isManager)
                      .build();
            })
            .toList();

      } finally {
        dtoSpan.end();
      }

      return new SliceImpl<>(dtoList, PageRequest.of(0, size), bundle.hasNext());

    } finally {
      span.end();
    }
  }


  /**
   * 채팅방 정보 조회
   */
  @Override
  @Transactional(readOnly = true)
  public ChatRoomInfoResponse getChatRoomInfoByChatId(Long chatId, Long userId) {

    //채팅룸 찾기
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);

    //참여여부
    boolean isParticipate = isParticipant(chatId, userId);

    //참여자수
    long partNum = chatPartRepository
        .countByChatRoom_ChatIdAndDeletedAtIsNull(chatId);

    //형태 변경
    return chatRoomMapper.toChatRoomInfoResponse(chatRoom, isParticipate, partNum);
  }

  /**
   * 채팅방 참여
   */
  @Override
  @Transactional
  public ChatPartResponse addParticipant(Long chatId, Long userId) {
    log.info("[chatId={}][userId={}] 채팅방 참여 요청", chatId, userId);

    // 1-1) 채팅방 존재 확인
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);

    // 1-2) 유저 존재 확인
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("[chatId={}][userId={}] 유저 정보를 찾을 수 없습니다.", chatId, userId);
          return new BusinessException(ErrorCode.USER_NOT_FOUND);
        });

    // 2) 이미 참여 중인지 확인 (중복 방지)
    validateNotAlreadyParticipant(chatId, userId);

    // 3) 참여자 생성
    ChatPart chatPart = ChatPart.create(chatRoom, user);

    chatPart = chatPartRepository.save(chatPart);

    log.info("[chatId={}][userId={}] 채팅방 참여 성공. chatPartId={}",
        chatId, userId, chatPart.getChatPartId());

    // 4) DTO 변환
    return ChatPartResponse.of(chatPart);
  }

  /**
   * 채팅 떠나기
   */
  @Transactional
  @Override
  public void leaveChat(Long chatId, Long userId) {
    log.info("[chatId={}][userId={}] 채팅방 나가기 요청", chatId, userId);

    getChatRoomOrThrow(chatId);

    // 활성 상태의 참여 기록 조회
    ChatPart chatPart = getChatPartOrThrow(chatId, userId);

    // 이미 나갔는지 확인할 필요 없음 (조건상 DeletedAt IS NULL 보장됨)
    chatPart.setDeletedAt(LocalDateTime.now());

    log.info("[chatId={}][userId={}] 채팅방 나가기 성공. chatPartId={}",
        chatId, userId, chatPart.getChatPartId());
  }

  /**
   * 채팅방 찾기 (없으면 오류)
   */
  private ChatRoom getChatRoomOrThrow(Long chatId) {
    return chatRoomRepository.findById(chatId)
        .orElseThrow(() -> {
          log.warn("[chatId={}] 채팅방이 존재하지 않습니다.", chatId);
          return new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        });
  }

  /** -- helper method -- */

  /**
   * 참여정보 찾기 (없으면 오류)
   */
  private ChatPart getChatPartOrThrow(Long chatId, Long userId) {
    return chatPartRepository
        .findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(chatId, userId)
        .orElseThrow(() -> {
          log.warn("[chatId={}][userId={}] 채팅 참여 정보를 확인할 수 없습니다.", chatId, userId);
          return new BusinessException(ErrorCode.CHAT_PARTICIPANT_NOT_FOUND);
        });
  }

  /**
   * 이미 참여 중인 사용자인지 확인 (이미 존재하면 오류)
   */
  // 1) 존재 여부만 보는 메서드
  private boolean isParticipant(Long chatId, Long userId) {
    return chatPartRepository
        .existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(chatId, userId);
  }

  // 2) 추가할 때만 쓰는 검증 메서드
  private void validateNotAlreadyParticipant(Long chatId, Long userId) {
    if (isParticipant(chatId, userId)) {
      log.warn("[chatId={}][userId={}] 이미 채팅방에 참여 중인 유저입니다.", chatId, userId);
      throw new BusinessException(ErrorCode.CHAT_PARTICIPANT_ALREADY_EXISTS);
    }
  }

  //메시지와 파일이 모두 없는지 여부 확인
  private void validateMessageOrFiles(String content, List<MultipartFile> files) {
    boolean noMessage = (content == null || content.trim().isEmpty());
    boolean noFiles = (files == null || files.isEmpty());

    //둘 다 없으면 오류 반환
    if (noMessage && noFiles) {
      log.warn("텍스트나 파일 중 하나 이상 보유해야 합니다.");
      throw new BusinessException(ErrorCode.MESSAGE_OR_FILE_REQUIRED);
    }
  }

  //부모 메시지 반환
  private Message getParentMessageIfExists(Long parentId, Long chatId) {
    //부모 id가 없으면 null 반환
    if (parentId == null) {
      return null;
    }
    //해당 id에 해당하는 부모 메시지가 없으면 오류 반환
    Message parent = messageRepository.findById(parentId)
        .orElseThrow(() -> {
          log.warn("[chatId={}][parentId={}] 부모 메시지를 찾을 수 없습니다.", chatId, parentId);
          return new BusinessException(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
        });

    //TODO: 캡슐화를 공부해보자!
    // 부모 메시지가 다른 방의 메시지면 막기
    if (!parent.isSameRoom(chatId)) {
      log.warn("[chatId={}][parentId={}] 부모 메시지가 다른 채팅방에 속해 있습니다.", chatId, parentId);
      throw new BusinessException(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
    }

    //부모 메시지 반환
    return parent;
  }

  //메시지 파일 저장 후 저장된 파일 리스트 반환
  private List<MessageAttachment> saveAttachments(Long chatId,
                                                  Long messageId,
                                                  List<MultipartFile> files,
                                                  Message message) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }

    List<MessageAttachment> result = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) continue;

      try {
        String storedUrl = fileStore.storeMessageFile(chatId, messageId, file);

        MessageAttachment att = MessageAttachment.create(message, file, storedUrl);

        result.add(attachmentRepository.save(att));

      } catch (IOException e) {
        throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
      }
    }
    return result;
  }

  //event 발행
  private void publishMessageEvents(MessageResponse dto) {
    messageEventPublisher.publishMessageCreated(dto);
    notificationEventPublisher.publishChatNotificationCreatedEvent(dto);
    log.info("이벤트 발행 완료. messageId={}", dto.getMessageId());
  }


}