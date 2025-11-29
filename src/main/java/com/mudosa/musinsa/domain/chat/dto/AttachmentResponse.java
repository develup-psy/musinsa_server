package com.mudosa.musinsa.domain.chat.dto;

import com.mudosa.musinsa.domain.chat.entity.MessageAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Attachment Response Dto")
public class AttachmentResponse {
  @Schema(description = "파일 id", example = "1")
  private Long attachmentId;
  @Schema(description = "파일 경로", example = "/upload/chat/1/4a1bc5a1-5149-4224-b117-08c33c0c55d2_파일1.png")
  private String attachmentUrl;
  @Schema(description = "파일 확장자", example = "image/png")
  private String mimeType;
  @Schema(description = "파일 크기", example = "3763")
  private long sizeBytes;

  public static AttachmentResponse of(MessageAttachment entity) {
    return AttachmentResponse.builder()
            .attachmentId(entity.getAttachmentId())
            .attachmentUrl(entity.getAttachmentUrl())
            .mimeType(entity.getMimeType())
            .sizeBytes(entity.getSizeBytes())
            .build();
  }
}
