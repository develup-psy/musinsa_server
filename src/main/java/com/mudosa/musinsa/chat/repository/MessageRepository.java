package com.mudosa.musinsa.chat.repository;

import com.mudosa.musinsa.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

  /**
   * 채팅방의 메시지 페이징 조회 (최신순)
   */

  @Query(
      value = """
          select m
            from Message m
            join fetch m.chatPart cp
            left join fetch cp.user u
            where m.chatId = :chatId
            order by m.createdAt desc, m.messageId desc
          """
  )
  Slice<Message> findSliceWithRelationsByChatId(
      @Param("chatId") Long chatId,
      Pageable pageable
  );

  /**
   * 채팅방 메시지 커서 기반 조회 (최신순)
   * <p>
   * - keyset(커서) 페이징용 쿼리
   * - 첫 페이지: cursorCreatedAt, cursorMessageId 에 null 전달
   * - 다음 페이지: 마지막으로 받은 메시지의 (createdAt, messageId)를 그대로 커서로 전달
   * <p>
   * 정렬/인덱스 조건:
   * - where m.chatId = :chatId
   * - (deleted_at IS NULL 은 엔티티 @Where 로 처리)
   * - order by m.createdAt desc, m.messageId desc
   * → 인덱스 (chat_id, deleted_at, created_at, message_id)와 정렬이 일치
   */
// 1) 첫 페이지: cursor 없음
  @Query("""
      select m.messageId
        from Message m
       where m.chatId = :chatId
         and m.deletedAt is null
       order by m.createdAt desc, m.messageId desc
      """)
  Slice<Long> findIdSliceByChatId(
      @Param("chatId") Long chatId,
      Pageable pageable
  );

  // 2) time + id 모드 (정석 keyset)
  @Query("""
      select m.messageId
        from Message m
       where m.chatId = :chatId
         and m.deletedAt is null
         and (
              m.createdAt < :cursorCreatedAt
           or (m.createdAt = :cursorCreatedAt and m.messageId < :cursorMessageId)
         )
       order by m.createdAt desc, m.messageId desc
      """)
  Slice<Long> findIdSliceByChatIdAndCursor(
      @Param("chatId") Long chatId,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorMessageId") Long cursorMessageId,
      Pageable pageable
  );

  /**
   * 부모 메시지들을 messageId 기준으로 한 번에 로딩
   * - parent 메시지 + 발신자(chatPart, user) fetch join
   * - 자식 메시지에서 parent.getChatPart().getUser() 접근 시 N+1 방지
   */
  @Query("""
      select distinct m
        from Message m
        join fetch m.chatPart cp
        left join fetch cp.user u
       where m.messageId in :messageIds
      """)
  List<Message> findAllByMessageIds(
      @Param("messageIds") List<Long> messageIds
  );
}
