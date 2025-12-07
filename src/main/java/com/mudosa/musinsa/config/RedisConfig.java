package com.mudosa.musinsa.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  @Value("${spring.data.redis.host}")
  private String redisHost;

  @Value("${spring.data.redis.port}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Value("${spring.data.redis.database:0}")
  private int redisDatabase;

  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
    standaloneConfig.setHostName(redisHost);
    standaloneConfig.setPort(redisPort);
    standaloneConfig.setDatabase(redisDatabase);

    if (redisPassword != null && !redisPassword.isEmpty()) {
      standaloneConfig.setPassword(redisPassword);
    }

    return new LettuceConnectionFactory(standaloneConfig);
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    // LocalDateTime 등 Java Time API 객체를 직렬화/역직렬화할 수 있도록 모듈 등록
    mapper.registerModule(new JavaTimeModule());

    // JSON에 알 수 없는 필드가 있어도 무시하도록 설정
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // ObjectMapper를 사용하여 GenericJackson2JsonRedisSerializer 인스턴스 생성
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(serializer); // 커스텀 Serializer 사용
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(serializer); // 커스텀 Serializer 사용

    template.afterPropertiesSet();
    return template;
  }
}
