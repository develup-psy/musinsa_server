package com.mudosa.musinsa.chat.service;

import com.mudosa.musinsa.chat.dto.WSMessageResponseDTO;
import com.mudosa.musinsa.chat.entity.Message;
import com.mudosa.musinsa.chat.event.ChatEventPublisher;
import com.mudosa.musinsa.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MessageCommandService {
  private final MessageRepository messageRepository;
  private final ChatEventPublisher chatEventPublisher;

  /**
   * <h5>메시지 저장</h5>
   * <p>
   * 첨부파일을 제외한 message를 저장한다.
   *
   * @param message         저장할 메시지
   * @param clientMessageId 메시지 구별 clientId
   * @return 저장된 메시지
   */
  @Transactional
  public Long saveContentMessage(Message message, String clientMessageId) {
    // 1. 메시지 저장
    Message savedMessage = messageRepository.save(message);
    WSMessageResponseDTO messageResponse = WSMessageResponseDTO.of(savedMessage, clientMessageId);

    // 2. 이벤트 발행 (메세지 저장 정보 반환)
    chatEventPublisher.publishBroadcastEvent(messageResponse.getChatId(), messageResponse);

    // 3. 저장된 메시지 반환
    return savedMessage.getMessageId();
  }

}
