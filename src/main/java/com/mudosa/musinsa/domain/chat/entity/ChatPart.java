package com.mudosa.musinsa.domain.chat.entity;

import com.mudosa.musinsa.domain.chat.enums.ChatPartRole;
import com.mudosa.musinsa.user.domain.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_part")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatPart {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "chat_part_id")
  private Long chatPartId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "chat_id", nullable = false)
  private ChatRoom chatRoom;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private ChatPartRole role; // USER, BRAND_ADMIN

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
      columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime createdAt;

  @Setter
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "chatPart", orphanRemoval = false)
  @Builder.Default
  private List<Message> messages = new ArrayList<>();

  public static ChatPart create(ChatRoom chatRoom, User user) {
    return ChatPart.builder()
            .chatRoom(chatRoom)
            .user(user)
            .role(ChatPartRole.USER)
            .build();
  }

}
