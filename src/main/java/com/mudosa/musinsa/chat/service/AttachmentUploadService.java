package com.mudosa.musinsa.chat.service;

import com.mudosa.musinsa.chat.dto.AttachmentResponse;
import com.mudosa.musinsa.chat.dto.WSFileUploadSuccessDTO;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.entity.MessageAttachment;
import com.mudosa.musinsa.chat.event.ChatEventPublisher;
import com.mudosa.musinsa.chat.event.TempUploadedFile;
import com.mudosa.musinsa.chat.file.FileStore;
import com.mudosa.musinsa.chat.repository.MessageAttachmentRepository;
import com.mudosa.musinsa.chat.repository.MessageRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentUploadService {
  private final MessageRepository messageRepository;
  private final MessageAttachmentRepository attachmentRepository;
  private final FileStore fileStore;
  private final ChatEventPublisher chatEventPublisher;
  private final TransactionTemplate transactionTemplate;

  /**
   * <h5>파일 저장</h5>
   * <p>
   * 메시지의 파일을 저장한다.
   *
   * @param messageId       메시지 Id
   * @param files           파일 목록
   * @param clientMessageId 구별을 위한 clientId
   */
  public void saveAttachments(Long messageId, List<TempUploadedFile> files, String clientMessageId) {
    // 0. 파일이 없으면 SKIP!
    if (files == null || files.isEmpty()) {
      return;
    }

    Message message = messageRepository.findById(messageId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

    // 1. S3 업로드 + 엔티티 생성 (여기서 실패한 파일 null)
    List<MessageAttachment> successAttachments =
        uploadFilesToS3AndBuildEntities(message, files);

    // 2. 결과 처리
    transactionTemplate.executeWithoutResult(status -> {
      // DB 저장 및 상태 변경 작업은 하나의 트랜잭션으로 묶여 실행됩니다.
      if (successAttachments.isEmpty()) {
        // 2-1) 전부 실패한 경우 -> 메시지 상태 FAILED
        markMessageFailed(message, clientMessageId);
      } else {
        // 2-2) 하나라도 성공한 경우 -> 성공한 것만 저장 및 알림 발송
        saveAttachmentsAndUpdateStatusToNormal(message, successAttachments, clientMessageId);
      }
    });
  }

  /**
   * <h5>S3 파일 저장</h5>
   * <p>
   * S3로 파일을 저장한다.
   *
   * @param message 메시지
   * @param files   파일 목록
   * @return 저장된 메시지 첨부파일 리스트
   * @implNote 예외 발생시 throw 하지 않고 null을 반환하여 성공한 것만 필터링
   */
  protected List<MessageAttachment> uploadFilesToS3AndBuildEntities(Message message, List<TempUploadedFile> files) {
    // 1. S3로 파일 저장
    List<CompletableFuture<MessageAttachment>> futures = files.stream()
        .map(file -> fileStore.storeMessageFile(message.getChatId(), message.getMessageId(), file)
            .thenApply(storedUrl -> MessageAttachment.create(message, file, storedUrl))
            // 개별 파일 업로드 실패 시 null 반환 (전체 로직 중단 방지)
            .exceptionally(ex -> {
              return null;
            })
        )
        .toList();

    // 2. join()으로 저장된 값 가져오기
    return futures.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull) // 성공한(null이 아닌) 객체만 수집
        .toList();
  }

  /**
   * <h5>DB 저장 & 메시지 상태 변경 및 WS으로 저장된 파일 전송</h5>
   * <p>
   * S3에 저장된 첨부파일 DB 저장 및 메시지 상태 NORMAL로 변경하고 WS으로 저장된 파일을 전송한다.
   *
   * @param message         메시지
   * @param attachments     파일 목록
   * @param clientMessageId 구별을 위한 clientId
   */
  protected void saveAttachmentsAndUpdateStatusToNormal(Message message, List<MessageAttachment> attachments, String clientMessageId) {
    // 1. 성공한 파일들만 DB 저장
    List<MessageAttachment> savedAttachments = attachmentRepository.saveAll(attachments);

    // 2. 메시지 상태 NORMAL
    message.markAsNormal();
    messageRepository.save(message);

    // 3. DTO 변환 (성공한 목록만 들어감)
    List<AttachmentResponse> responses = savedAttachments.stream()
        .map(AttachmentResponse::of)
        .toList();

    // 4. 저장된 파일 목록 dto로 변경
    WSFileUploadSuccessDTO dto = WSFileUploadSuccessDTO.of(message, responses);

    // 5. 웹소켓 전송 (성공한 파일 목록)
    chatEventPublisher.publishBroadcastEvent(dto.getChatId(), dto);
  }

  /**
   * <h5>메시지 상태 변경 및 WS 전송</h5>
   * <p>
   * 메시지 상태를 FAIL로 변경하고 빈배열을 ws으로 전송
   *
   * @param message         메시지
   * @param clientMessageId 구별을 위한 clientId
   */
  protected void markMessageFailed(Message message, String clientMessageId) {
    // 1. 메시지 상태 FAIL
    message.markAsFailed();
    messageRepository.save(message);

    // 2. 저장된 파일 목록 dto로 변경
    WSFileUploadSuccessDTO dto = WSFileUploadSuccessDTO.of(message, List.of());

    // 3. 웹소켓 전송 (빈 배열)
    chatEventPublisher.publishBroadcastEvent(dto.getChatId(), dto);
  }
}