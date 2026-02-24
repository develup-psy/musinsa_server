package com.mudosa.musinsa.chat.repository;

import com.mudosa.musinsa.chat.entity.ChatPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatPartRepository extends JpaRepository<ChatPart, Long> {

  // 발신자를 제외한 참여자 조회
  @Query("SELECT cp FROM ChatPart cp JOIN FETCH cp.user JOIN FETCH cp.chatRoom cr JOIN FETCH cr.brand WHERE cp.user.id != :userId AND cp.chatRoom.chatId = :chatId AND cp.deletedAt IS NULL")
  List<ChatPart> findChatPartsExcludingUser(@Param("userId") Long userId, @Param("chatId") Long chatId);

  // 활성 여부 exists (퇴장 전)
  boolean existsByChatRoom_ChatIdAndUser_IdAndDeletedAtIsNull(Long chatId, Long userId);


  Optional<ChatPart> findByChatRoom_ChatIdAndUserIdAndDeletedAtIsNull(Long chatId, Long userId);

  long countByChatRoom_ChatIdAndDeletedAtIsNull(Long chatId);
}
