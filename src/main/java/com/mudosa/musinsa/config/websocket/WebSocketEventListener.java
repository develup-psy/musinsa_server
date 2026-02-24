package com.mudosa.musinsa.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 연결/해제 이벤트 리스너
 * 사용자의 온라인 상태를 추적하고 알림을 전송
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

  private final SimpMessageSendingOperations messagingTemplate;

  // 세션 ID와 사용자 정보를 매핑
  private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

  @EventListener
  public void handleWebSocketConnectListener(SessionConnectedEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = headerAccessor.getSessionId();
    Long userId = getUserIdFromHeaders(headerAccessor);
    if (userId != null) {
      sessionUserMap.put(sessionId, userId);
      log.info("새로운 WebSocket 연결: sessionId={}, userId={}", sessionId, userId);
    } else {
      log.info("WebSocket 연결: sessionId={}, userId 없음", sessionId);
    }
  }

  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = headerAccessor.getSessionId();
    Long userId = sessionUserMap.remove(sessionId);
    if (userId != null) {
      log.info("WebSocket 연결 해제: sessionId={}, userId={}", sessionId, userId);
    } else {
      log.info("WebSocket 연결 해제: sessionId={}, userId 정보 없음", sessionId);
    }
  }


  /**
   * 헤더에서 사용자 ID 추출 (예시)
   */
  private Long getUserIdFromHeaders(StompHeaderAccessor headerAccessor) {
    // 실제로는 JWT 토큰을 파싱하거나 인증 정보에서 추출
    String userIdStr = headerAccessor.getFirstNativeHeader("userId");
    return userIdStr != null ? Long.parseLong(userIdStr) : null;
  }
}