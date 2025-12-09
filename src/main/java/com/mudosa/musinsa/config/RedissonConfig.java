package com.mudosa.musinsa.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionMinimumIdleSize(4)      // 최소 유휴 커넥션
                .setConnectionPoolSize(16)            // 커넥션 풀 크기
                .setIdleConnectionTimeout(10000)      // 유휴 커넥션 타임아웃
                .setConnectTimeout(3000)              // 연결 타임아웃
                .setTimeout(3000)                     // 명령 타임아웃
                .setRetryAttempts(3)                  // 재시도 횟수
                .setRetryInterval(1000);              // 재시도 간격

        // Netty 스레드 최적화 (코어 수 기반)
        config.setNettyThreads(0);  // 0 = 자동 설정 (availableProcessors * 2)
        config.setThreads(0);

        return Redisson.create(config);
    }
}