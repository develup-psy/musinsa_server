package com.mudosa.musinsa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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

  @Value("${inventory.redis.host:${spring.data.redis.host}}")
  private String inventoryRedisHost;

  @Value("${inventory.redis.port:${spring.data.redis.port}}")
  private int inventoryRedisPort;

  @Value("${inventory.redis.password:${spring.data.redis.password:}}")
  private String inventoryRedisPassword;

  @Value("${inventory.redis.database:${spring.data.redis.database:0}}")
  private int inventoryRedisDatabase;

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    return new LettuceConnectionFactory(buildStandaloneConfig(redisHost, redisPort, redisPassword, redisDatabase));
  }

  @Bean(name = "inventoryRedisConnectionFactory")
  public RedisConnectionFactory inventoryRedisConnectionFactory() {
    return new LettuceConnectionFactory(
            buildStandaloneConfig(inventoryRedisHost, inventoryRedisPort, inventoryRedisPassword, inventoryRedisDatabase)
    );
  }

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    ObjectMapper mapper = builder.build();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean
  @Primary
  public StringRedisTemplate stringRedisTemplate(
          @Qualifier("redisConnectionFactory") RedisConnectionFactory redisConnectionFactory
  ) {
    StringRedisTemplate template = new StringRedisTemplate();
    template.setConnectionFactory(redisConnectionFactory);
    template.afterPropertiesSet();
    return template;
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

  @Bean(name = "inventoryStringRedisTemplate")
  public StringRedisTemplate inventoryStringRedisTemplate(
          @Qualifier("inventoryRedisConnectionFactory") RedisConnectionFactory inventoryRedisConnectionFactory
  ) {
    StringRedisTemplate template = new StringRedisTemplate();
    template.setConnectionFactory(inventoryRedisConnectionFactory);
    template.afterPropertiesSet();
    return template;
  }

  @Bean
  public RedisScript<Long> acquireRateSlotScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/acquire-rate-slot.lua"));
    script.setResultType(Long.class);
    return script;
  }

  @Bean
  public RedisScript<Long> atomicDecreaseStockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/decrease-stock-batch.lua"));
    script.setResultType(Long.class);
    return script;
  }

  @Bean
  public RedisScript<Long> atomicIncreaseStockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/increase-stock-batch.lua"));
    script.setResultType(Long.class);
    return script;
  }

  private RedisStandaloneConfiguration buildStandaloneConfig(
          String host,
          int port,
          String password,
          int database
  ) {
    RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
    standaloneConfig.setHostName(host);
    standaloneConfig.setPort(port);
    standaloneConfig.setDatabase(database);

    if (password != null && !password.isEmpty()) {
      standaloneConfig.setPassword(password);
    }
    return standaloneConfig;
  }
}
