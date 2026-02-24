package com.mudosa.musinsa.chat.facade;

import com.mudosa.musinsa.chat.entity.ChatPart;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.enums.MessageStatus;
import com.mudosa.musinsa.chat.event.ChatEventPublisher;
import com.mudosa.musinsa.chat.event.TempUploadedFile;
import com.mudosa.musinsa.chat.service.ChatRoomService;
import com.mudosa.musinsa.chat.service.MessageCommandService;
import com.mudosa.musinsa.chat.service.MessageQueryService;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageFacade {
  private final ChatRoomService chatRoomService;
  private final MessageCommandService messageCommandService;
  private final MessageQueryService messageQueryService;
  private final ChatEventPublisher chatEventPublisher;

  /**
   * <h5>메시지 저장</h5>
   * <p>
   * 발송된 메시지를 저장 및 ws 발송.
   *
   * @param chatId          채팅방 아이디
   * @param userId          사용자 아이디
   * @param parentId        참조 메시지(부모 메시지)
   * @param content         메시지 내용
   * @param files           첨부파일 리스트
   * @param now             발송 시간
   * @param clientMessageId 메시지 구분을 위한 clientId
   * @return 전송 성공 여부
   * @throws BusinessException MESSAGE_OR_FILE_REQUIRED  - 빈 메시지인 경우
   * @throws BusinessException CHAT_NOT_FOUND - 채팅방이 없을 때
   * @throws BusinessException CHAT_PARTICIPANT_NOT_FOUND - 채팅 참여 중이지 않을 때
   * @throws BusinessException MESSAGE_PARENT_NOT_FOUND - 부모 메시지가 없거나 해당 채팅방의 메시지가 아닐 때
   */
  @Transactional
  public void saveMessage(Long chatId, Long userId, Long parentId, String content, List<MultipartFile> files, LocalDateTime now, String clientMessageId) {
    // 1. 검증
    // 1-1) 메시지 존재 검증
    validateMessageOrFiles(content, files);

    // 1-2) 채팅방 존재 검증
    chatRoomService.getChatRoomOrThrow(chatId);

    // 1-3) 참여자 존재 검증
    ChatPart chatPart = chatRoomService.getChatPartOrThrow(chatId, userId);

    // 1-4) 부모 메시지 검증 및 조회
    Message parent = messageQueryService.getParentMessageIfExists(parentId, chatId);

    // 2. 파일 존재 여부에 따라 메시지 상태 결정
    boolean hasRealFile = files != null && files.stream().anyMatch(f -> !f.isEmpty());
    MessageStatus status = (!hasRealFile) ? MessageStatus.NORMAL : MessageStatus.UPLOADING;

    // 3. 메시지 저장(파일 제외)
    // 3-1) 메시지 엔티티 생성
    Message message = Message.createMessage(content, now, chatPart, parent, status);
    // 3-2) 메시지 저장 (메시지 ID 반환)
    Long savedMessageId = messageCommandService.saveContentMessage(message, clientMessageId);

    // 4. 파일 비동기 처리(파일 존재 시)
    if (hasRealFile) {
      List<TempUploadedFile> tempFiles = files.stream()
          .map(f -> {
            try {
              String suffix = Optional.ofNullable(f.getOriginalFilename())
                  .filter(name -> name.contains("."))
                  .map(name -> name.substring(name.lastIndexOf('.')))
                  .orElse("");

              Path tempPath = Files.createTempFile("chat-upload-", suffix);
              log.debug("임시 파일 경로: " + tempPath.toAbsolutePath());
              try (InputStream in = f.getInputStream();
                   OutputStream out = Files.newOutputStream(tempPath)) {

                in.transferTo(out); // 내부적으로 버퍼링, 전체를 힙에 안 올림
              }

              return new TempUploadedFile(
                  f.getOriginalFilename(),
                  f.getContentType(),
                  tempPath,
                  Files.size(tempPath)
              );
            } catch (IOException e) {
              throw new IllegalStateException("파일 임시 저장 실패: " + f.getOriginalFilename(), e);
            }
          })
          .toList();

      chatEventPublisher.publishUploadEvent(savedMessageId, tempFiles, clientMessageId);
    }
  }

  @Transactional
  public void resaveAttachment(Long chatId, Long userId, Long messageId, List<MultipartFile> files, LocalDateTime now, String clientMessageId) {
    // 1. 검증
    // 1-1) 채팅방 존재 검증
    chatRoomService.getChatRoomOrThrow(chatId);

    // 1-2) 참여자 존재 검증
    ChatPart chatPart = chatRoomService.getChatPartOrThrow(chatId, userId);

    // 1-3) 메시지, 채팅방 일치 검증

    // 1-4) 메시지, 작성자 일치 검증

    // 2. 파일 존재 여부 검증
    boolean hasRealFile = files != null && files.stream().anyMatch(f -> !f.isEmpty());

    // 4. 파일 비동기 처리(파일 존재 시)
    if (hasRealFile) {
      // 4-1) MultiPart 파일은 Http 응답시 사라지므로 복사
      List<TempUploadedFile> tempFiles = files.stream()
          .map(f -> {
            try {
              String suffix = Optional.ofNullable(f.getOriginalFilename())
                  .filter(name -> name.contains("."))
                  .map(name -> name.substring(name.lastIndexOf('.')))
                  .orElse("");

              Path tempPath = Files.createTempFile("chat-upload-", suffix);
//              log.error("임시 파일 경로: " + tempPath.toAbsolutePath());

              try (InputStream in = f.getInputStream();
                   OutputStream out = Files.newOutputStream(tempPath)) {

                in.transferTo(out); // 내부적으로 버퍼링, 전체를 힙에 안 올림
              }

              return new TempUploadedFile(
                  f.getOriginalFilename(),
                  f.getContentType(),
                  tempPath,
                  Files.size(tempPath)
              );
            } catch (IOException e) {
              throw new IllegalStateException("파일 임시 저장 실패: " + f.getOriginalFilename(), e);
            }
          })
          .toList();

      chatEventPublisher.publishUploadEvent(messageId, tempFiles, "retry");
    }
  }


  /**
   * <h5>메시지와 파일이 모두 없는지 검증</h5>
   *
   * @param content 메시지 내용
   * @param files   첨부파일 리스트
   * @throws BusinessException MESSAGE_OR_FILE_REQUIRED  - 빈 메시지인 경우
   */
  private void validateMessageOrFiles(String content, List<MultipartFile> files) {
    boolean noMessage = (content == null || content.trim().isEmpty());
    boolean noFiles = (files == null || files.isEmpty());

    //둘 다 없으면 오류 반환
    if (noMessage && noFiles) {
      log.warn("텍스트나 파일 중 하나 이상 보유해야 합니다.");
      throw new BusinessException(ErrorCode.MESSAGE_OR_FILE_REQUIRED);
    }
  }

}
