package com.mudosa.musinsa.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Entity
@Table(name = "message_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MessageAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long attachmentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false)
  @Setter(AccessLevel.PUBLIC)
  private Message message;

  @Column(name = "attachment_url", nullable = false, length = 1024)
  private String attachmentUrl;

  @Column(name = "mime_type", length = 100)
  private String mimeType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  public static MessageAttachment create(Message message, MultipartFile file, String storedUrl) {
    return MessageAttachment.builder()
            .attachmentUrl(storedUrl)
            .message(message)
            .mimeType(file.getContentType())
            .sizeBytes(file.getSize())
            .build();
  }
}
