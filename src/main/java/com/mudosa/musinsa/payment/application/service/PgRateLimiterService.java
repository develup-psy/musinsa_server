package com.mudosa.musinsa.payment.application.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgRateLimiterService {

    private final StringRedisTemplate redisTemplate;

    @Qualifier("acquireRateSlotScript")
    private final RedisScript<Long> acquireRateSlotScript;

    @Getter
    @Value("${pg.rate-limit.max-requests:100}")
    private long maxRequests;

    @Value("${pg.rate-limit.window-millis:1000}")
    private long windowMillis;

    @Value("${pg.rate-limit.redis-key:payment_rate_window:default}")
    private String rateWindowKey;

    public boolean tryAcquireRateSlot(String scope, String idempotencyHint) {
        long nowMillis = System.currentTimeMillis();
        String member = scope + ":" + idempotencyHint + ":" + UUID.randomUUID();

        try {
            Long result = redisTemplate.execute(
                    acquireRateSlotScript,
                    List.of(rateWindowKey),
                    String.valueOf(nowMillis),
                    String.valueOf(windowMillis),
                    String.valueOf(maxRequests),
                    member
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("PG rate slot 획득 실패: scope={}, idempotencyHint={}", scope, idempotencyHint, e);
            return false;
        }
    }
}
