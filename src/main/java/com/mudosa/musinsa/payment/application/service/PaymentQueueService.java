package com.mudosa.musinsa.payment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.payment.application.dto.EnqueueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<String> enqueuePaymentScript;
    private final ObjectMapper objectMapper;

    @Value("${pg.queue.max-size:10000}")
    private int maxQueueSize;

    @Value("${pg.rate-limit.max-requests:100}")
    private int batchSize;

    private static final String QUEUE_KEY_PREFIX = "payment_queue:event:";
    private static final String DEFAULT_QUEUE_KEY = "payment_queue:default";

    /**
     * 결제 요청을 대기열에 등록
     * Lua 스크립트로 ZADD + ZRANK를 원자적으로 실행
     */
    public EnqueueResult enqueue(Long paymentId) {
        return enqueue(paymentId, null);
    }

    /**
     * 이벤트별 대기열에 결제 요청 등록
     */
    public EnqueueResult enqueue(Long paymentId, Long eventId) {
        String key = resolveKey(eventId);
        double score = System.nanoTime() / 1_000_000.0; // ms 단위 정밀도

        String result = redisTemplate.execute(
                enqueuePaymentScript,
                List.of(key),
                String.valueOf(score),
                paymentId.toString(),
                String.valueOf(maxQueueSize)
        );

        if (result == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "대기열 등록에 실패했습니다");
        }

        return parseEnqueueResult(result);
    }

    /**
     * 대기열에서 배치 크기만큼 꺼내기 (ZPOPMIN: 원자적 추출 + 삭제)
     */
    public Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> popBatch(Long eventId) {
        String key = resolveKey(eventId);
        return redisTemplate.opsForZSet().popMin(key, batchSize);
    }

    /**
     * 기본 대기열에서 배치 꺼내기
     */
    public Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> popBatch() {
        return popBatch(null);
    }

    /**
     * 현재 대기 순번 조회 (0-based)
     */
    public Long getPosition(Long paymentId, Long eventId) {
        String key = resolveKey(eventId);
        return redisTemplate.opsForZSet().rank(key, paymentId.toString());
    }

    public Long getPosition(Long paymentId) {
        return getPosition(paymentId, null);
    }

    /**
     * 전체 대기열 크기
     */
    public Long getQueueSize(Long eventId) {
        String key = resolveKey(eventId);
        return redisTemplate.opsForZSet().zCard(key);
    }

    public Long getQueueSize() {
        return getQueueSize(null);
    }

    /**
     * 예상 대기 시간 (초)
     */
    public long estimateWaitSeconds(long rank) {
        if (rank <= 0) return 1;
        return (rank / batchSize) + 1;
    }

    private String resolveKey(Long eventId) {
        if (eventId != null) {
            return QUEUE_KEY_PREFIX + eventId;
        }
        return DEFAULT_QUEUE_KEY;
    }

    private EnqueueResult parseEnqueueResult(String json) {
        try {
            var node = objectMapper.readTree(json);
            return EnqueueResult.builder()
                    .status(node.get("status").asText())
                    .rank(node.get("rank").asLong())
                    .total(node.get("total").asLong())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("대기열 결과 파싱 실패: {}", json, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "대기열 결과 파싱에 실패했습니다");
        }
    }
}
