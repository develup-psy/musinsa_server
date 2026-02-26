package com.mudosa.musinsa.chat.repository;

import com.mudosa.musinsa.chat.entity.MessageAttachment;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

  /**
   * 메시지 목록 내의 모든 첨부파일 조회
   */
  @Query("""
        select ma
        from MessageAttachment ma
        where ma.message.messageId in :messageIds
      """)
  List<MessageAttachment> findAllByMessageIdIn(@Param("messageIds") Collection<Long> messageIds);

}
