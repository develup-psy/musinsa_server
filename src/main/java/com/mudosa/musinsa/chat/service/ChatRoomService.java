package com.mudosa.musinsa.chat.service;

import com.mudosa.musinsa.chat.dto.ChatPartResponse;
import com.mudosa.musinsa.chat.dto.ChatRoomInfoResponse;
import com.mudosa.musinsa.chat.entity.ChatPart;
import com.mudosa.musinsa.chat.entity.ChatRoom;
import com.mudosa.musinsa.chat.repository.ChatPartRepository;
import com.mudosa.musinsa.chat.repository.ChatRoomRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {
  private final ChatRoomRepository chatRoomRepository;
  private final ChatPartRepository chatPartRepository;
  private final UserRepository userRepository;

  /**
   * <h5>채팅방 정보 조회</h5>
   * <p>
   * 채팅방 정보를 조회한다.
   *
   * @param chatId 채팅방 아이디
   * @param userId 사용자 아이디
   * @return 채팅방 정보 DTO (+ 사용자 참여 여부)
   * @throws BusinessException CHAT_NOT_FOUND  - 채팅방이 존재하지 않을 때
   */
  public ChatRoomInfoResponse getChatRoomInfoByChatId(Long chatId, Long userId) {

    // 1. 채팅방 검증 및 조회
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);

    // 2. 참여 여부 조회
    boolean isParticipate = isParticipant(chatId, userId);

    // 3. 참여자수 조회
    long partNum = chatPartRepository
        .countByChatRoom_ChatIdAndDeletedAtIsNull(chatId);

    // 4. 결과 반환
    return ChatRoomInfoResponse.of(chatRoom, isParticipate, partNum);
  }

  /**
   * <h5>채팅방 참여 처리</h5>
   * <p>
   * 사용자가 채팅방에 참여한다.
   *
   * @param chatId 채팅방 아이디
   * @param userId 사용자 아이디
   * @return 참여 정보 응답 DTO
   * @throws BusinessException CHAT_NOT_FOUND  - 채팅방이 존재하지 않을 때
   * @throws BusinessException CHAT_PARTICIPANT_ALREADY_EXISTS  - 이미 참여중일 때
   */
  @Transactional
  public ChatPartResponse addParticipant(Long chatId, Long userId) {

    // 1. 채팅방 존재 검증 및 조회
    ChatRoom chatRoom = getChatRoomOrThrow(chatId);

    // 2. 유저 존재 검증 및 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 3. 참여 여부 검증 (중복 참여 방지)
    validateNotAlreadyParticipant(chatId, userId);

    // 4. 참여 정보 저장
    ChatPart chatPart = chatPartRepository.save(ChatPart.create(chatRoom, user));

    // 5. 결과 반환
    return ChatPartResponse.of(chatPart);
  }

  /**
   * <h5>채팅 떠나기</h5>
   * <p>
   * 사용자가 채팅방을 떠난다.
   *
   * @param chatId 채팅방 아이디
   * @param userId 사용자 아이디
   * @throws BusinessException CHAT_NOT_FOUND  - 채팅방이 존재하지 않을 때
   * @throws BusinessException CHAT_PARTICIPANT_NOT_FOUND  - 참여 중이지 않을 때
   */
  @Transactional
  public void leaveChat(Long chatId, Long userId) {
    // 1. 채팅방 존재 검증 및 조회
    getChatRoomOrThrow(chatId);

    // 2. 참여 여부 확인 (참여 중인 경우에만 나가기 가능)
    ChatPart chatPart = getChatPartOrThrow(chatId, userId);

    // 3. 채팅방 나가기
    chatPart.setDeletedAt(LocalDateTime.now());
  }

  /**
   * <h5>내 채팅방 조회</h5>
   * <p>
   * 사용자가 참여 중인 모든 채팅방 목록을 조회한다.
   *
   * @param userId 사용자 아이디
   * @return 채팅방 DTO 리스트
   *
   */
  public List<ChatRoomInfoResponse> getChatRoomByUserId(Long userId) {
    // 1. 사용자가 참여중인 채팅방 조회 (나간 채팅방 제외)
    List<ChatRoom> chatRooms =
        chatRoomRepository.findDistinctByParts_User_IdAndParts_DeletedAtIsNull(userId);

    // 2. DTO List 형태로 변환
    return chatRooms.stream()
        .map(chatRoom -> ChatRoomInfoResponse.of(chatRoom, true))
        .toList();
  }

  /**
   * <h5>채팅방 찾기 (없으면 오류)</h5>
   *
   * @return 채팅방
   * @throws BusinessException CHAT_NOT_FOUND - 채팅방이 없을 때
   */
  public ChatRoom getChatRoomOrThrow(Long chatId) {
    return chatRoomRepository.findById(chatId)
        .orElseThrow(() -> {
          log.warn("[chatId={}] 채팅방이 존재하지 않습니다.", chatId);
          return new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        });
  }

  /**
   * <h5>참여정보 찾기</h5>
   *
   * @return 참여 정보
   * @throws BusinessException CHAT_PARTICIPANT_NOT_FOUND - 채팅 참여 중이지 않을 때
   */
  public ChatPart getChatPartOrThrow(Long chatId, Long userId) {
    return chatPartRepository
        .findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(chatId, userId)
        .orElseThrow(() -> {
          log.warn("[chatId={}][userId={}] 채팅 참여 정보를 확인할 수 없습니다.", chatId, userId);
          return new BusinessException(ErrorCode.CHAT_PARTICIPANT_NOT_FOUND);
        });
  }

  /**
   * <h5>이미 참여한 사용자인 경우 오류 반환</h5>
   *
   * @throws BusinessException CHAT_PARTICIPANT_ALREADY_EXISTS  - 이미 참여 중일 때
   */
  private void validateNotAlreadyParticipant(Long chatId, Long userId) {
    if (isParticipant(chatId, userId)) {
      log.warn("[chatId={}][userId={}] 이미 채팅방에 참여 중인 유저입니다.", chatId, userId);
      throw new BusinessException(ErrorCode.CHAT_PARTICIPANT_ALREADY_EXISTS);
    }
  }

  /**
   * <h5>참여 여부 반환</h5>
   *
   * @return 참여 여부(boolean)
   */
  private boolean isParticipant(Long chatId, Long userId) {
    return chatPartRepository
        .existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(chatId, userId);
  }
}
