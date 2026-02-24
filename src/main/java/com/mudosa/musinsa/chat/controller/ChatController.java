package com.mudosa.musinsa.chat.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.mudosa.musinsa.chat.dto.ChatPartResponse;
import com.mudosa.musinsa.chat.dto.ChatRoomInfoResponse;
import com.mudosa.musinsa.chat.dto.MessageResponse;
import com.mudosa.musinsa.common.dto.ApiResponse;
import com.mudosa.musinsa.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * <Swagger 설명을 위한 interface>
 * 채팅 REST API 컨트롤러
 * - 채팅방 목록 조회
 * - 메시지 히스토리 조회 (페이징)
 * - 채팅방 생성/삭제
 * - 파일 업로드 등
 */
@Tag(name = "Chat API", description = "채팅 API")
public interface ChatController {

  /**
   * 채팅 메시지 전송
   * POST /api/chat/{chatId}/send
   */
  @Operation(
      summary = "메시지 전송",
      description = "특정 채팅방에 텍스트 또는 이미지를 전송합니다. "
          + "텍스트 메시지(`message`)와 이미지 파일(`files`)은 모두 선택적으로 포함 가능합니다."
  )
  ResponseEntity<Void> sendMessage(
      @Parameter(description = "채팅방 ID", example = "1", required = true)
      @PathVariable Long chatId,

      @AuthenticationPrincipal CustomUserDetails userDetails,

      @Parameter(description = "답장 대상 메시지 ID (없을 경우 null)", example = "2")
      @RequestParam(value = "parentId", required = false) Long parentId,

      @Parameter(
          description = "텍스트 메시지",
          schema = @Schema(example = "오늘도 좋은 하루입니다 😊"))
      @RequestPart(value = "message", required = false) String message,
      @RequestPart(required = false) String clientMessageId,
      @Parameter(description = "전송할 이미지 파일 리스트")
      @RequestPart(value = "files", required = false) List<MultipartFile> files) throws FirebaseMessagingException;

  /**
   * 채팅방 이전 메시지 조회 (페이징)
   * GET /api/chat/1/messages?cursorCreatedAt=2025-11-17T19:00:00&cursorMessageId=123
   */
  @Operation(
      summary = "메시지 조회",
      description = "특정 채팅방의 메시지를 조회합니다. (페이지 처리)"
  )
  ApiResponse<Slice<MessageResponse>> getChatMessages(
      @Parameter(description = "채팅방 ID", example = "2025-11-17T15:33:36", required = true)
      @PathVariable Long chatId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "이전 마지막 메시지 날짜", example = "1")
      @RequestParam(required = false) String cursorCreatedAt,
      @Parameter(description = "이전 마지막 메시지 ID", example = "1")
      @RequestParam(required = false)
      Long cursorMessageId,
      @RequestParam(defaultValue = "20") int size
  );

  /**
   * 채팅방 정보 조회
   * GET /api/chat/1/info
   */
  @Operation(
      summary = "채팅방 정보 조회",
      description = "특정 채팅방의 정보를 조회합니다"
  )
  ApiResponse<ChatRoomInfoResponse> getChatInfo(
      @Parameter(description = "채팅방 ID", example = "1", required = true)
      @PathVariable Long chatId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  );


  /**
   * 채팅방 참가
   * POST /api/chat/1/participants
   */
  @Operation(
      summary = "채팅방 참가",
      description = "특정 채팅방에 참여합니다."
  )
  ApiResponse<ChatPartResponse> addParticipant(
      @Parameter(description = "채팅방 ID", example = "1", required = true)
      @PathVariable Long chatId,
      @AuthenticationPrincipal CustomUserDetails userDetails);

  /**
   * 채팅방 나가기
   * PATCH /api/chat/1/leave
   */
  @Operation(
      summary = "채팅방 나가기",
      description = "특정 채팅방에서 퇴장합니다."
  )
  ApiResponse<List<ChatRoomInfoResponse>> leaveChat(
      @Parameter(description = "채팅방 ID", example = "1", required = true)
      @PathVariable Long chatId,
      @AuthenticationPrincipal CustomUserDetails userDetails);

  /**
   * 나의 참가 채팅방 조회
   * GET /api/chat/my
   */
  @Operation(
      summary = "내 채팅방 목록 조회",
      description = "내가 참가하고 있는 채팅방 목록을 조회합니다."
  )
  @GetMapping("/my")
  ApiResponse<List<ChatRoomInfoResponse>> getMyChat(
      @AuthenticationPrincipal CustomUserDetails userDetails);


}