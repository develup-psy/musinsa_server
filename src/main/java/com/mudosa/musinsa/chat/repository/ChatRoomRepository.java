package com.mudosa.musinsa.chat.repository;

import com.mudosa.musinsa.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

  List<ChatRoom> findDistinctByParts_User_IdAndParts_DeletedAtIsNull(Long userId);
}
