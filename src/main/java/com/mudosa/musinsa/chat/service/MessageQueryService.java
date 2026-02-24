package com.mudosa.musinsa.chat.service;

import com.mudosa.musinsa.brand.domain.repository.BrandMemberRepository;
import com.mudosa.musinsa.chat.dto.*;
import com.mudosa.musinsa.chat.entity.ChatRoom;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.repository.ChatRoomRepository;
import com.mudosa.musinsa.chat.repository.MessageAttachmentRepository;
import com.mudosa.musinsa.chat.repository.MessageRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageQueryService {
  private final MessageRepository messageRepository;
  private final MessageAttachmentRepository attachmentRepository;
  private final BrandMemberRepository brandMemberRepository;
  private final ChatRoomRepository chatRoomRepository;

  /**
   * <h5>메시지 조회</h5>
   * <p>
   * 메시지 정보를 조합하여 반환한다.
   *
   * @param chatId 채팅방 아이디
   * @param cursor 범위 위치
   * @param size   사이즈(메시지 개수)
   * @return 메시지 리스트 DTO
   */
  public Slice<MessageResponse> getChatMessages(Long chatId, MessageCursor cursor, int size) {
    // 1. 메시지 조회
    MessagesBundle bundle = loadMessages(chatId, cursor, size);

    // 2. 메시지가 없으면 빈 배열 반환
    if (bundle.messages().isEmpty()) {
      return new SliceImpl<>(List.of(), PageRequest.of(0, size), false);
    }

    // 3. DTO 매핑
    List<MessageResponse> dtoList = mapToMessageResponseList(bundle);

    // 4. 반환
    return new SliceImpl<>(dtoList, PageRequest.of(0, size), bundle.hasNext());
  }

  /**
   * <h5>실제 메시지 목록 조회</h5>
   * <p>
   * 실제 메시지 정보들을 조회한다.
   *
   * @param chatId 채팅방 아이디
   * @param cursor 범위 위치
   * @param size   사이즈(메시지 개수)
   * @return 메시지 관련 정보 번들
   * @throws BusinessException CHAT_NOT_FOUND - 채팅방이 없을 때
   */
  private MessagesBundle loadMessages(Long chatId, MessageCursor cursor, int size) {

    // 1. 반환할 변수들 생성
    List<Message> orderedMessages;
    boolean hasNext;
    Set<Long> managerUserIds;
    Map<Long, List<AttachmentResponse>> attachmentMap;
    Map<Long, Message> parentMap;

    // 2. 채팅방 유효성 검증
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);

    // 3. 메시지 ID 목록 조회 (keyset)
    Slice<Long> idSlice = getChatMessageIds(chatId, cursor, size);

    // 3-1) 메시지 ID 목록
    List<Long> ids = idSlice.getContent();
    if (ids.isEmpty()) {
      // 목록이 비어있으면 빈 메시지 번들 반환
      return MessagesBundle.empty();
    }
    List<Long> pageIds = ids.size() > size ? ids.subList(0, size) : ids;

    // 3-2) 다음 메시지 여부
    hasNext = idSlice.hasNext();

    // 4. 실제 메시지 정보 불러오기 (ID 목록 이용)
    List<Message> messages = messageRepository.findAllByMessageIds(pageIds);

    // 4-1) DB에서 받은 ID 순서를 그대로 살리기 위해 Map → pageIds 순으로 재조합
    Map<Long, Message> messageMap = messages.stream()
        .collect(Collectors.toMap(Message::getMessageId, Function.identity()));

    orderedMessages = pageIds.stream()
        .map(messageMap::get)
        .filter(Objects::nonNull)
        .toList();

    // 4-2) 부모 메시지 ID 목록 생성(중복 제거)
    List<Long> parentIds;
    Set<Long> parentIdSet = orderedMessages.stream()
        .map(Message::getParent)
        .filter(Objects::nonNull)
        .map(Message::getMessageId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    parentIds = new ArrayList<>(parentIdSet);


    // 4-3) 부모 메시지 조회
    parentMap = Collections.emptyMap();
    if (!parentIds.isEmpty()) {
      List<Message> parentMessages = messageRepository.findAllByMessageIds(parentIds);
      parentMap = parentMessages.stream()
          .collect(Collectors.toMap(Message::getMessageId, Function.identity()));
    }

    // 4-4) 관리자 목록 조회
    Long brandId = chatRoom.getBrand().getBrandId();
    List<Long> managerIds = brandMemberRepository.findActiveUserIdsByBrandId(brandId);
    managerUserIds = new HashSet<>(managerIds);

    // 4-5) 메시지/부모 ID 합치기 (첨부 조회용)
    Set<Long> allIdSet = new LinkedHashSet<>();
    for (Message msg : orderedMessages) {
      allIdSet.add(msg.getMessageId());
    }
    allIdSet.addAll(parentIds);
    List<Long> allIds = new ArrayList<>(allIdSet);

    // 4-6) 첨부 조회
    attachmentMap = attachmentRepository.findAllByMessageIdIn(allIds).stream()
        .collect(Collectors.groupingBy(
            ma -> ma.getMessage().getMessageId(),
            Collectors.mapping(AttachmentResponse::of, Collectors.toList())
        ));

    // 5) 최종 Bundle 반환
    return new MessagesBundle(orderedMessages, hasNext, managerUserIds, attachmentMap, parentMap);
  }

  /**
   * <h5>메시지 ID 목록 조회</h5>
   * <p>
   * 메시지 정보 없이 ID 목록만을 조회한다.
   *
   * @param chatId 채팅방 아이디
   * @param cursor 범위 위치
   * @param size   사이즈(메시지 개수)
   * @return 메시지 ID 리스트 DTO
   */
  private Slice<Long> getChatMessageIds(Long chatId, MessageCursor cursor, int size) {
    // 1. hasNext 판단 위해 size+1개 조회
    Pageable pageable = PageRequest.of(0, size + 1);

    // 2-1) cursor 없으면 첫페이지 조회
    if (cursor == null || cursor.messageId() == null) {
      return messageRepository.findIdSliceByChatId(chatId, pageable);
    }

    // 2-2) cursor 있으면 해당하는 cursor 이전의 메시지 조회
    return messageRepository.findIdSliceByChatIdAndCursor(chatId, cursor.createdAt(), cursor.messageId(), pageable);
  }


  /**
   * <h5>채팅방 조회</h5>
   *
   * @throws BusinessException CHAT_NOT_FOUND - 채팅방이 없을 때
   */
  private ChatRoom getChatRoomOrThrow(Long chatId) {
    return chatRoomRepository.findById(chatId)
        .orElseThrow(() -> {
          log.warn("[chatId={}] 채팅방이 존재하지 않습니다.", chatId);
          return new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        });
  }

  /**
   * <h5>message List -> messageResponse List 변환</h5>
   *
   * @return messageResponse List
   */
  @NotNull
  private List<MessageResponse> mapToMessageResponseList(MessagesBundle bundle) {
    // 1. 정보 가져오기
    List<Message> messages = bundle.messages();
    Set<Long> managerUserIds = bundle.managerUserIds();
    Map<Long, List<AttachmentResponse>> attachmentMap = bundle.attachmentMap();
    Map<Long, Message> parentMap = bundle.parentMap();

    // 2. mapping
    return messages.stream()
        .map(msg -> {
          // 2-1) 메시지 첨부 파일 가져옴
          List<AttachmentResponse> currentAttachments =
              attachmentMap.getOrDefault(msg.getMessageId(), Collections.emptyList());

          // 2-2) parent 엔티티를 parentMap에서 가져옴
          Message parent = null;
          if (msg.getParent() != null) {
            Long parentId = msg.getParent().getMessageId();
            parent = parentMap.get(parentId);
          }

          // 2-3) ParentMessageResponse 생성
          ParentMessageResponse parentDto = null;
          if (parent != null) {
            // 부모 메시지 첨부파일 가져옴
            List<AttachmentResponse> parentAttachments =
                attachmentMap.getOrDefault(parent.getMessageId(), Collections.emptyList());
            // 생성 완료
            parentDto = ParentMessageResponse.of(parent, parentAttachments);
          }

          // 2-4) 매니저 여부 조회
          boolean isManager = managerUserIds.contains(msg.getChatPart().getUser().getId());

          // 2-5) 정보 조합
          return MessageResponse.of(msg, currentAttachments, parentDto, isManager);
        })
        .toList();
  }

  /**
   * <h5>부모 메시지 조회</h5>
   *
   * @return 부모 메시지
   * @throws BusinessException MESSAGE_PARENT_NOT_FOUND - 부모 메시지가 없거나 해당 채팅방의 메시지가 아닐 때
   */
  public Message getParentMessageIfExists(Long parentId, Long chatId) {
    // 부모 id가 없으면 null 반환
    if (parentId == null) {
      return null;
    }
    // 해당 id에 해당하는 부모 메시지가 없으면 오류 반환
    Message parent = messageRepository.findById(parentId)
        .orElseThrow(() -> {
          log.warn("[chatId={}][parentId={}] 부모 메시지를 찾을 수 없습니다.", chatId, parentId);
          return new BusinessException(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
        });

    if (!parent.isSameRoom(chatId)) {
      throw new BusinessException(ErrorCode.MESSAGE_PARENT_NOT_FOUND);
    }

    //부모 메시지 반환
    return parent;
  }

}
