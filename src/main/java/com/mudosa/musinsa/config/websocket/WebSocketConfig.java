package com.mudosa.musinsa.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${spring.rabbitmq.host}")
  public String rabbitHost;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")
        .withSockJS();

    registry.addEndpoint("/ws-native")
        .setAllowedOriginPatterns("*");
  }

//  @Override
//  public void configureMessageBroker(MessageBrokerRegistry config) {
//    config.enableSimpleBroker("/topic", "/qna");
//    config.setApplicationDestinationPrefixes("/app");
//    config.setUserDestinationPrefix("/user");
//  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost(rabbitHost)
        .setRelayPort(61613)
        .setClientLogin("guest")
        .setClientPasscode("guest")
        .setSystemLogin("guest")
        .setSystemPasscode("guest")
        .setSystemHeartbeatReceiveInterval(0)
        .setSystemHeartbeatSendInterval(0)
    ;

    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }


  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
        .corePoolSize(8)
        .maxPoolSize(16)
        .queueCapacity(5000)
        .keepAliveSeconds(60);
  }

  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
        .corePoolSize(16)      // 기본 스레드 수
        .maxPoolSize(64)       // burst 대응
        .queueCapacity(500)  // 메시지가 밀릴 때 버퍼
        .keepAliveSeconds(60);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        // 서버가 한 세션으로 최대 얼마나 쌓았다가 보낼 수 있는지 (기본 512KB)
        .setSendBufferSizeLimit(2 * 1024 * 1024) // 예: 2MB로 상향

        // 개별 STOMP 메시지 최대 크기 (원하면 같이 조정)
        .setMessageSizeLimit(1024 * 512) // 256KB 정도로 제한

        // 한 메시지 전송에 쓸 수 있는 최대 시간
        .setSendTimeLimit(10_000); // 20초
  }
}
