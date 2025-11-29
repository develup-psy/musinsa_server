package com.mudosa.musinsa.domain.chat.controller;

import com.mudosa.musinsa.domain.chat.dto.ChatPartResponse;
import com.mudosa.musinsa.domain.chat.dto.ChatRoomInfoResponse;
import com.mudosa.musinsa.domain.chat.dto.MessageCursor;
import com.mudosa.musinsa.domain.chat.dto.MessageResponse;
import com.mudosa.musinsa.domain.chat.enums.ChatRoomType;
import com.mudosa.musinsa.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChatController 테스트")
class ChatControllerImplTest extends ControllerTestSupport {

  /* === Test Helper === */
  private ChatRoomInfoResponse createChatRoomInfoResponse(Long chatId) {
    return ChatRoomInfoResponse.builder()
            .brandId(1L)
            .chatId(chatId)
            .type(ChatRoomType.GROUP)
            .brandNameKo("브랜드")
            .isParticipate(true)
            .partNum(2L)
            .build();
  }

  private MessageResponse createMessageResponse(Long chatId, Long messageId, String content) {
    return MessageResponse.builder()
            .chatId(chatId)
            .messageId(messageId)
            .content(content)
            .build();
  }

  private ChatPartResponse createChatPart(CustomUserDetails userDetails, Long chatId) {
    return ChatPartResponse.builder()
            .chatPartId(1L)
            .userId(userDetails.getUserId())
            .chatId(chatId)
            .userName("사용자" + userDetails.getUserId())
            .role("USER")
            .build();
  }

  private MockMultipartFile createMessagePart(String content) {
    return new MockMultipartFile("message", "", "text/plain", content.getBytes());
  }

  private MockMultipartFile createFilePart(String filename, String content) {
    return new MockMultipartFile("files", filename, "text/plain", content.getBytes());
  }

  /* === PATCH /api/chat/1/leave Api 테스트 [채팅방 나가기] === */
  @Nested
  @DisplayName("PATCH /api/chat/{chatId}/leave")
  class LeaveChatRoomsApi {
    @DisplayName("채팅방 나가기 후 남아 있는 채팅방 목록을 반환한다")
    @Test
    void leaveChat() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      ChatRoomInfoResponse chat1 = createChatRoomInfoResponse(chatId);
      List<ChatRoomInfoResponse> response = List.of(chat1);

      given(chatService.getChatRoomByUserId(eq(userDetails.getUserId())))
          .willReturn(response);

      // when & then
      mockMvc.perform(patch("/api/chat/{chatId}/leave", chatId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("채팅방에서 성공적으로 퇴장하셨습니다."))
          .andExpect(jsonPath("$.data[0].chatId").value(chatId))
          .andExpect(jsonPath("$.data[0].participate").value(true))
          .andExpect(jsonPath("$.data.length()").value(1));

      InOrder inOrder = inOrder(chatService);
      inOrder.verify(chatService).leaveChat(chatId, userDetails.getUserId());
      inOrder.verify(chatService).getChatRoomByUserId(userDetails.getUserId());
      inOrder.verifyNoMoreInteractions();
    }

    @DisplayName("채팅방 나가기 후 남은 채팅방이 없으면 빈 배열을 반환한다")
    @Test
    void leaveChat_noChat_Left() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      List<ChatRoomInfoResponse> response = List.of();

      given(chatService.getChatRoomByUserId(eq(userDetails.getUserId())))
          .willReturn(response);

      // when & then
      mockMvc.perform(patch("/api/chat/{chatId}/leave", chatId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("채팅방에서 성공적으로 퇴장하셨습니다."))
          .andExpect(jsonPath("$.data.length()").value(0));

      InOrder inOrder = inOrder(chatService);
      inOrder.verify(chatService).leaveChat(chatId, userDetails.getUserId());
      inOrder.verify(chatService).getChatRoomByUserId(userDetails.getUserId());
      inOrder.verifyNoMoreInteractions();
    }

    @DisplayName("채팅방을 나갈 때 채팅 ID는 숫자여야 한다")
    @Test
    void leaveChat_invalidChatId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      String invalidChatId = "invalid";

      // when & then
      mockMvc.perform(patch("/api/chat/{chatId}/leave", invalidChatId)
              .with(user(userDetails))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.errorCode").value("400"))
          .andExpect(jsonPath("$.message").value(String.format("요청 파라미터 ‘chatId’의 값 ‘%s’은(는) 올바르지 않습니다. Long 형식의 값이어야 합니다.", invalidChatId)));

      verifyNoInteractions(chatService);
    }


    @DisplayName("인증되지 않은 사용자가 채팅방 나가기 요청을 하면 401을 반환한다")
    @Test
    void leaveChat_unauthenticated() throws Exception {
      // given
      Long chatId = 1L;

      // when & then
      mockMvc.perform(patch("/api/chat/{chatId}/leave", chatId)
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }

    @DisplayName("CSRF 없이 채팅방 나가기를 요청하면 403을 반환한다")
    @Test
    void leaveChat_withoutCsrf() throws Exception {
      // given
      CustomUserDetails user = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      mockMvc.perform(patch("/api/chat/{chatId}/leave", chatId)
              .with(user(user)))
          .andDo(print())
          .andExpect(status().isForbidden());

      verifyNoInteractions(chatService);
    }
  }

  /* === GET /api/chat/my Api 테스트 [내 채팅방 목록 조회] === */
  @Nested
  @DisplayName("GET /api/chat/my")
  class GetMyChatRoomsApi {
    @DisplayName("내 채팅방 목록 조회 요청 시 참여 중인 채팅방 목록을 반환한다")
    @Test
    void getMyChat() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");

      ChatRoomInfoResponse chat1 = createChatRoomInfoResponse(1L);
      ChatRoomInfoResponse chat2 = createChatRoomInfoResponse(2L);

      List<ChatRoomInfoResponse> response = List.of(chat1, chat2);

      given(chatService.getChatRoomByUserId(eq(userDetails.getUserId())))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/chat/my")
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("나의 참여 채팅방 목록이 성공적으로 조회되었습니다."))
          .andExpect(jsonPath("$.data.length()").value(2))
          .andExpect(jsonPath("$.data[0].chatId").value(1))
          .andExpect(jsonPath("$.data[1].chatId").value(2));

      verify(chatService).getChatRoomByUserId(eq(userDetails.getUserId()));
    }

    @DisplayName("참여 중인 채팅방이 없으면 빈 배열을 반환한다")
    @Test
    void getMyChat_empty() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");

      given(chatService.getChatRoomByUserId(userDetails.getUserId()))
          .willReturn(List.of());

      // when & then
      mockMvc.perform(get("/api/chat/my")
              .with(user(userDetails))
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.length()").value(0));

      verify(chatService).getChatRoomByUserId(userDetails.getUserId());
    }

    @DisplayName("인증되지 않은 사용자가 내 채팅방 목록을 요청하면 401을 반환한다")
    @Test
    void getMyChat_unauthorized() throws Exception {
      // when & then
      mockMvc.perform(get("/api/chat/my"))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }
  }

  /* === POST /api/chat/{chatId}/participants Api 테스트 [채팅방 참가] === */
  @Nested
  @DisplayName("POST /api/chat/{chatId}/participants")
  class ParticipateChatRoomApi {
    @DisplayName("채팅방 참여 요청 성공 시 참여 정보를 반환한다")
    @Test
    void addParticipant() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      ChatPartResponse response = createChatPart(userDetails, chatId);

      given(chatService.addParticipant(eq(chatId), eq(userDetails.getUserId())))
          .willReturn(response);

      // when & then
      mockMvc.perform(post("/api/chat/{chatId}/participants", chatId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("채팅방에 성공적으로 참여했습니다."))
          .andExpect(jsonPath("$.data.chatPartId").value(1L))
          .andExpect(jsonPath("$.data.userId").value(userDetails.getUserId()))
          .andExpect(jsonPath("$.data.chatId").value(chatId));

      verify(chatService).addParticipant(chatId, userDetails.getUserId());
    }

    @DisplayName("채팅방 참여 요청 시 채팅 ID는 숫자여야 한다")
    @Test
    void addParticipant_invalidChatId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      String invalidChatId = "invalid";

      // when & then
      mockMvc.perform(post("/api/chat/{chatId}/participants", invalidChatId)
              .with(user(userDetails))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.errorCode").value("400"))
          .andExpect(jsonPath("$.message").value(String.format("요청 파라미터 ‘chatId’의 값 ‘%s’은(는) 올바르지 않습니다. Long 형식의 값이어야 합니다.", invalidChatId)));

      verifyNoInteractions(chatService);
    }


    @DisplayName("인증되지 않은 사용자가 참여 요청하면 401을 반환한다")
    @Test
    void addParticipant_unauthenticated() throws Exception {
      // given
      Long chatId = 1L;

      // when & then
      mockMvc.perform(post("/api/chat/{chatId}/participants", chatId)
              .with(csrf())
          )
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }

    @DisplayName("CSRF 토큰 없이 참여 요청 시 403을 반환한다")
    @Test
    void addParticipant_withoutCsrf() throws Exception {
      // given
      Long chatId = 1L;
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");

      // when & then
      mockMvc.perform(post("/api/chat/{chatId}/participants", chatId)
              .with(user(userDetails))
          )
          .andDo(print())
          .andExpect(status().isForbidden());

      verifyNoInteractions(chatService);
    }
  }

  /* === GET /api/chat/1/info Api 테스트 [채팅방 정보 조회] === */
  @Nested
  @DisplayName("GET /api/chat/{chatId}/info")
  class GetChatRoomInfoApi {
    @DisplayName("채팅방 정보를 조회하면 해당 채팅방 정보를 반환한다")
    @Test
    void getChatInfo() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");

      Long chatId = 1L;
      ChatRoomInfoResponse response = createChatRoomInfoResponse(chatId);

      given(chatService.getChatRoomInfoByChatId(chatId, userDetails.getUserId()))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/info", chatId)
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.chatId").value(chatId))
          .andExpect(jsonPath("$.data.participate").value(true))
          .andExpect(jsonPath("$.message").value("채팅방의 정보를 성공적으로 조회했습니다."));

      verify(chatService).getChatRoomInfoByChatId(chatId, userDetails.getUserId());
    }

    @DisplayName("채팅방 정보를 조회할 때 채팅 ID는 숫자여야 한다")
    @Test
    void getChatInfo_invalidChatId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      String invalidChatId = "invalid";

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/info", invalidChatId)
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.errorCode").value("400"))
          .andExpect(jsonPath("$.message").value(String.format("요청 파라미터 ‘chatId’의 값 ‘%s’은(는) 올바르지 않습니다. Long 형식의 값이어야 합니다.", invalidChatId)));

      verifyNoInteractions(chatService);
    }

    @DisplayName("인증되지 않은 사용자가 채팅 정보를 조회하면 401을 반환한다")
    @Test
    void getChatInfo_unauthorized() throws Exception {
      // given
      Long chatId = 1L;

      //when & then
      mockMvc.perform(get("/api/chat/{chatId}/info", chatId))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }
  }

  /* === GET /api/chat/1/messages?page=0&size=20 Api 테스트 [채팅방 이전 메시지 조회(페이징)] === */
  @Nested
  @DisplayName("GET /api/chat/{chatId}/messages")
  class GetMessagesApi {
    @DisplayName("채팅방 이전 메시지 조회 요청 시 페이지별 메시지를 반환한다")
    @Test
    void getChatMessages() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");

      Long chatId = 1L;
      int size = 20;

      List<MessageResponse> messages = new ArrayList<>();
      for (long i = 1L; i <= size; i++) {
        messages.add(createMessageResponse(chatId, i, "안녕" + i));
      }

      Slice<MessageResponse> response = new SliceImpl<>(new ArrayList<>(messages));

      //  첫 페이지는 cursor=null 이므로 이렇게 stub
      given(chatService.getChatMessages(eq(chatId), isNull(), eq(size)))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("size", String.valueOf(size))
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value(containsString("이전 메시지를 성공적으로 조회했습니다")))
          .andExpect(jsonPath("$.data.size").value(size))
          .andExpect(jsonPath("$.data.content", hasSize(size)));

      verify(chatService).getChatMessages(eq(chatId), isNull(), eq(size));
    }

    @DisplayName("채팅방 이전 메시지가 없으면 빈 페이지 정보를 반환한다")
    @Test
    void getChatMessages_empty() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      int size = 20;
      Slice<MessageResponse> response = new SliceImpl<>(new ArrayList<>());

      // 첫 페이지 → cursor = null
      given(chatService.getChatMessages(eq(chatId), isNull(), eq(size)))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("size", String.valueOf(size))
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(containsString("이전 메시지를 성공적으로 조회했습니다")))
          .andExpect(jsonPath("$.data.content", hasSize(0)));

      verify(chatService).getChatMessages(eq(chatId), isNull(), eq(size));
    }

    @DisplayName("size 미지정 시 기본값(20)으로 메시지를 조회한다")
    @Test
    void getChatMessages_defaultParams() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      int size = 20; // 기본값

      // 첫 페이지 → cursor=null 이므로 isNull()로 매칭
      given(chatService.getChatMessages(eq(chatId), isNull(), eq(size)))
          .willReturn(new SliceImpl<>(List.of()));

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .with(user(userDetails))) // size 파라미터 없음 → 기본값 20
          .andDo(print())
          .andExpect(status().isOk());

      verify(chatService).getChatMessages(eq(chatId), isNull(), eq(size));
    }

    @DisplayName("채팅방 이전 메시지 조회 시 채팅 ID는 숫자여야 한다")
    @Test
    void getChatMessages_invalidChatId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      String invalidChatId = "invalid";

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", invalidChatId)
              .with(user(userDetails)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.errorCode").value("400"))
          .andExpect(jsonPath("$.message").value(String.format("요청 파라미터 ‘chatId’의 값 ‘%s’은(는) 올바르지 않습니다. Long 형식의 값이어야 합니다.", invalidChatId)));

      verifyNoInteractions(chatService);
    }

    @DisplayName("cursorCreatedAt, cursorMessageId가 모두 있으면 MessageCursor로 파싱해서 서비스로 전달한다")
    @Test
    void getChatMessages_withValidCursorParams() throws Exception {
      // given
      Long chatId = 1L;
      Long userId = 2L;
      String cursorCreatedAt = "2025-11-17T15:33:36";
      Long cursorMessageId = 100L;
      int size = 20;

      CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

      Slice<MessageResponse> slice = new SliceImpl<>(List.of()); // 빈 결과라도 상관 없음

      given(chatService.getChatMessages(eq(chatId), any(MessageCursor.class), eq(size)))
          .willReturn(slice);

      // when
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("cursorCreatedAt", cursorCreatedAt)
              .param("cursorMessageId", cursorMessageId.toString())
              .param("size", String.valueOf(size))
              .with(user(userDetails)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("이전 메시지를 성공적으로 조회했습니다"));

      // then
      ArgumentCaptor<MessageCursor> cursorCaptor = ArgumentCaptor.forClass(MessageCursor.class);

      verify(chatService).getChatMessages(eq(chatId), cursorCaptor.capture(), eq(size));

      MessageCursor cursor = cursorCaptor.getValue();
      assertThat(cursor).isNotNull();
      assertThat(cursor.createdAt())
          .isEqualTo(LocalDateTime.of(2025, 11, 17, 15, 33, 36));
      assertThat(cursor.messageId()).isEqualTo(cursorMessageId);
    }

    @DisplayName("cursor 파라미터가 없으면 cursor = null로 서비스가 호출된다")
    @Test
    void getChatMessages_withoutCursorParams() throws Exception {
      // given
      Long chatId = 1L;
      Long userId = 2L;
      int size = 20;

      CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");
      Slice<MessageResponse> slice = new SliceImpl<>(List.of());

      given(chatService.getChatMessages(eq(chatId), isNull(), eq(size)))
          .willReturn(slice);

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("size", String.valueOf(size))
              .with(user(userDetails)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("이전 메시지를 성공적으로 조회했습니다"))
          .andExpect(jsonPath("$.data.content", hasSize(0)));

      verify(chatService).getChatMessages(chatId, null, size);
    }

    @DisplayName("cursorCreatedAt만 있고 cursorMessageId가 없으면 cursor = null로 서비스가 호출된다")
    @Test
    void getChatMessages_onlyCursorCreatedAt() throws Exception {
      // given
      Long chatId = 1L;
      Long userId = 2L;
      int size = 20;
      String cursorCreatedAt = "2025-11-17T15:33:36";

      CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");
      Slice<MessageResponse> slice = new SliceImpl<>(List.of());

      given(chatService.getChatMessages(eq(chatId), isNull(), eq(size)))
          .willReturn(slice);

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("cursorCreatedAt", cursorCreatedAt)
              .param("size", String.valueOf(size))
              .with(user(userDetails)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("이전 메시지를 성공적으로 조회했습니다"));

      verify(chatService).getChatMessages(chatId, null, size);
    }

    @DisplayName("잘못된 cursorCreatedAt 포맷이면 IllegalArgumentException 처리로 400 응답을 반환한다")
    @Test
    void getChatMessages_invalidCursorCreatedAtFormat() throws Exception {
      // given
      Long chatId = 1L;
      Long userId = 2L;
      String invalidCursor = "2020:01:01T10-12-30"; // 잘못된 포맷

      CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("cursorCreatedAt", invalidCursor)
              .with(user(userDetails)))
          .andExpect(status().is5xxServerError())
          .andExpect(jsonPath("$.message")
              .value("cursorCreatedAt는 yyyy-MM-dd'T'HH:mm:ss 형식이어야 합니다."));

      // 서비스는 호출되지 않아야 함
      verify(chatService, never()).getChatMessages(anyLong(), any(), anyInt());
    }

    @DisplayName("인증되지 않은 사용자가 메시지 조회를 요청하면 401을 반환한다")
    @Test
    void getChatMessages_unauthenticated() throws Exception {
      // given
      Long chatId = 1L;

      int size = 20;

      // when & then
      mockMvc.perform(get("/api/chat/{chatId}/messages", chatId)
              .param("size", String.valueOf(size)))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }
  }

  /* === POST /api/chat/1/send Api 테스트 [채팅 메시지 전송] === */
  @Nested
  @DisplayName("POST /api/chat/{chatId}/send")
  class SendMessageApi {
    @DisplayName("답장 메시지와 파일을 함께 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_messageAndFiles() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      Long parentId = 1L;
      String content = "안녕";

      MessageResponse messageResponse = createMessageResponse(chatId, 1L, content);

      MockMultipartFile messagePart = createMessagePart(content);
      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          eq(content),
          anyList(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .file(file1)
              .file(file2)
              .param("parentId", String.valueOf(parentId))
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          eq(content),
          anyList(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("일반 메시지와 파일을 함께 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_messageAndFiles_withoutParentId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      String content = "안녕";

      MessageResponse messageResponse = createMessageResponse(chatId, 1L, content);

      MockMultipartFile messagePart = createMessagePart(content);
      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          eq(content),
          anyList(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .file(file1)
              .file(file2)
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          eq(content),
          anyList(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("답장 형태로 일반 메시지만 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_message() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      Long parentId = 1L;
      String content = "안녕";

      MessageResponse messageResponse = createMessageResponse(chatId, 1L, content);

      MockMultipartFile messagePart = createMessagePart(content);

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          eq(content),
          isNull(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .param("parentId", String.valueOf(parentId))
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          eq(content),
          isNull(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("일반 메시지만 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_message_withoutParentId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      String content = "안녕";

      MessageResponse messageResponse = createMessageResponse(chatId, 1L, content);

      MockMultipartFile messagePart = createMessagePart(content);

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          eq(content),
          isNull(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          eq(content),
          isNull(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("답장 형태로 파일만 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_files() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      Long parentId = 1L;

      MessageResponse messageResponse = MessageResponse.builder()
              .chatId(chatId)
              .build();

      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          isNull(),
          anyList(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(file1)
              .file(file2)
              .param("parentId", String.valueOf(parentId))
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          eq(parentId),
          isNull(),
          anyList(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("파일만 전송하면 성공 응답을 반환한다")
    @Test
    void sendMessage_files_withoutParentId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;

      MessageResponse messageResponse = createMessageResponse(chatId, 1L, null);

      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      given(chatService.saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          isNull(),
          anyList(),
          any(LocalDateTime.class)
      )).willReturn(messageResponse);

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(file1)
              .file(file2)
              .with(user(userDetails))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("메시지를 성공적으로 전송했습니다."));

      verify(chatService).saveMessage(
          eq(chatId),
          eq(userDetails.getUserId()),
          isNull(),
          isNull(),
          anyList(),
          any(LocalDateTime.class)
      );
    }

    @DisplayName("메시지 전송 시 채팅 ID는 숫자여야 한다")
    @Test
    void sendMessage_invalidChatId() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      String invalidChatId = "invalid";

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", invalidChatId)
              .with(user(userDetails))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.errorCode").value("400"))
          .andExpect(jsonPath("$.message").value(String.format("요청 파라미터 ‘chatId’의 값 ‘%s’은(는) 올바르지 않습니다. Long 형식의 값이어야 합니다.", invalidChatId)));

      verifyNoInteractions(chatService);
    }


    @DisplayName("인증되지 않은 사용자가 메시지 전송을 요청하면 401을 반환한다")
    @Test
    void sendMessage_unauthenticated() throws Exception {
      // given
      Long chatId = 1L;
      Long parentId = 1L;
      String content = "안녕";

      MockMultipartFile messagePart = createMessagePart(content);
      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .file(file1)
              .file(file2)
              .param("parentId", String.valueOf(parentId))
              .with(csrf())
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(chatService);
    }

    @DisplayName("CSRF 없이 메시지 전송을 요청하면 403을 반환한다")
    @Test
    void sendMessage_withoutCsrf() throws Exception {
      // given
      CustomUserDetails userDetails = new CustomUserDetails(1L, "USER");
      Long chatId = 1L;
      Long parentId = 1L;
      String content = "안녕";

      MockMultipartFile messagePart = createMessagePart(content);
      MockMultipartFile file1 = createFilePart("a.txt", "hello");
      MockMultipartFile file2 = createFilePart("b.txt", "world");

      // when & then
      mockMvc.perform(multipart("/api/chat/{chatId}/send", chatId)
              .file(messagePart)
              .file(file1)
              .file(file2)
              .param("parentId", String.valueOf(parentId))
              .with(user(userDetails))
              .header("Accept", "application/json")
          )
          .andDo(print())
          .andExpect(status().isForbidden());

      verifyNoInteractions(chatService);
    }
  }
}