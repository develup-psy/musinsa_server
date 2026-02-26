package com.mudosa.musinsa.chat.dto;

import com.mudosa.musinsa.chat.entity.MessageAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Attachment Response Dto")
public class AttachmentResponse {
  @Schema(description = "파일 경로", example = "/upload/chat/1/4a1bc5a1-5149-4224-b117-08c33c0c55d2_파일1.png")
  private String attachmentUrl;

  public static AttachmentResponse of(MessageAttachment entity) {
    return AttachmentResponse.builder()
        .attachmentUrl(entity.getAttachmentUrl())
        .build();
  }
}
