package com.mudosa.musinsa.chat.dto;

import com.mudosa.musinsa.chat.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Parent Message Response Dto")
public class ParentMessageResponse {
  @Schema(description = "메시지 id", example = "1")
  private Long messageId;
  @Schema(description = "유저 이름", example = "홍길동")
  private String userName;
  @Schema(description = "메시지 내용", example = "안녕하세요?")
  private String content;
  @Schema(description = "메시지 내 첨부파일 리스트")
  private List<AttachmentResponse> attachments;

  public static ParentMessageResponse of(Message parent, List<AttachmentResponse> parentAttachments) {
    return ParentMessageResponse.builder()
        .messageId(parent.getMessageId())
        .userName(parent.getChatPart().getUser().getUserName())
        .content(parent.getContent())
        .attachments(parentAttachments)
        .build();
  }

}
