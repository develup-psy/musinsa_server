package com.mudosa.musinsa.chat.entity;


import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.chat.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatRoom {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "chat_id")
  private Long chatId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 10)
  private ChatRoomType type; // GROUP, DM

  @Setter
  @Column(name = "last_message_at")
  private LocalDateTime lastMessageAt;

  // DB 기본값 사용 (CURRENT_TIMESTAMP / ON UPDATE)
  @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
      columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
      columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
  private LocalDateTime updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  // ==== 연관관계 ====
  @OneToMany(mappedBy = "chatRoom", orphanRemoval = false)
  @Builder.Default
  @Where(clause = "deleted_at IS NULL")
  private List<ChatPart> parts = new ArrayList<>();
}
