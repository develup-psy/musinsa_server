package com.mudosa.musinsa.chat.entity;

import com.mudosa.musinsa.chat.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE message SET deleted_at = CURRENT_TIMESTAMP, status = 'DELETED' WHERE message_id = ?")
@Where(clause = "deleted_at IS NULL")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;
  // 발신자: NULL 허용
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_part_id")
  private ChatPart chatPart;
  // 방 기준 조회용 FK -> 비정규화
  @Column(name = "chat_id", nullable = false)
  private Long chatId;
  // 답장(스레드) 자기참조
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Message parent;
  @Column(name = "content", columnDefinition = "TEXT")
  private String content;
  @Column(name = "created_at", nullable = false, updatable = false,
      columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime createdAt;
  @Column(name = "deleted_at")
  @Setter
  private LocalDateTime deletedAt;
  @Builder.Default
  @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MessageAttachment> attachments = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private MessageStatus status;

  public static Message createMessage(String content, LocalDateTime now, ChatPart chatPart, Message parent, MessageStatus status) {
    return Message.builder()
        .chatPart(chatPart)
        .chatId(chatPart.getChatRoom().getChatId())
        .content(StringUtils.hasText(content) ? content.trim() : null)
        .parent(parent)
        .createdAt(now)
        .status(status)
        .build();
  }

  public boolean isSameRoom(Long chatId) {
    ChatPart cp = this.getChatPart();
    return cp.getChatRoom().getChatId().equals(chatId);
  }

  public void updateStatus(MessageStatus status) {
    this.status = status;
  }

  public void markAsFailed() {
    this.status = MessageStatus.FAILED;
  }

  public void markAsUploaded() {
    this.status = MessageStatus.UPLOADING;
  }

  public void markAsDeleted() {
    this.status = MessageStatus.DELETED;
    this.deletedAt = LocalDateTime.now();
  }

  public void markAsNormal() {
    this.status = MessageStatus.NORMAL;
  }

}
