package com.mudosa.musinsa.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mudosa.musinsa.chat.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "FileUploadSuccess Response Dto")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSFileUploadSuccessDTO {
  private String type = "A";
  private Long messageId;
  private Long chatId;
  private List<AttachmentResponse> attachments;

  public static WSFileUploadSuccessDTO of(Message message, List<AttachmentResponse> responses) {

    return WSFileUploadSuccessDTO.builder()
        .type("A")
        .messageId(message.getMessageId())
        .chatId(message.getChatId())
        .attachments(responses)
        .build();
  }
}
