package com.mudosa.musinsa.payment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.payment.application.dto.EnqueueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueueService {

    private final StringRedisTemplate redisTemplate;

    @Qualifier("enqueuePaymentScript")
    private final RedisScript<String> enqueuePaymentScript;

    private final PgRateLimiterService pgRateLimiterService;
    private final ObjectMapper objectMapper;

    @Value("${pg.queue.batch-size:100}")
    private int batchSize;

    private static final String DEFAULT_QUEUE_KEY = "payment_queue:default";

    public EnqueueResult enqueue(Long paymentId, LocalDateTime createdAt) {
        double score = toEpochMillis(createdAt);

        String result = redisTemplate.execute(
                enqueuePaymentScript,
                List.of(DEFAULT_QUEUE_KEY),
                String.valueOf(score),
                paymentId.toString()
        );

        if (result == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "대기열 등록에 실패했습니다");
        }

        return parseEnqueueResult(result);
    }

    public Set<ZSetOperations.TypedTuple<String>> popBatch() {
        return redisTemplate.opsForZSet().popMin(DEFAULT_QUEUE_KEY, batchSize);
    }

    public Long getPosition(Long paymentId) {
        return redisTemplate.opsForZSet().rank(DEFAULT_QUEUE_KEY, paymentId.toString());
    }

    public long getMaxRequests() {
        return pgRateLimiterService.getMaxRequests();
    }

    public boolean tryAcquireRateSlot(Long paymentId) {
        return pgRateLimiterService.tryAcquireRateSlot("queue", String.valueOf(paymentId));
    }

    public void requeue(Long paymentId, Double score) {
        double requeueScore = (score != null) ? score : System.currentTimeMillis();
        redisTemplate.opsForZSet().add(DEFAULT_QUEUE_KEY, paymentId.toString(), requeueScore);
    }

    public Long estimateWaitSeconds(Long rank) {
        if (rank == null) {
            return null;
        }
        return (rank / getMaxRequests()) + 1;
    }

    private double toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return System.currentTimeMillis();
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private EnqueueResult parseEnqueueResult(String json) {
        try {
            var node = objectMapper.readTree(json);
            return EnqueueResult.builder()
                    .status(node.path("status").asText("QUEUED"))
                    .rank(node.path("rank").asLong(-1))
                    .total(node.path("total").asLong(0))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("대기열 결과 파싱 실패: {}", json, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "대기열 결과 파싱에 실패했습니다");
        }
    }
}
