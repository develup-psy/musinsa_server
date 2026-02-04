package com.mudosa.musinsa.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private final OrderRepository orderRepository;
    private final Cache<String, List<OrderItem>> orderItemsLocalCache;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ORDER_ITEMS_KEY = "orderItems:";

    private final ObjectMapper objectMapper;

    private static final Duration ORDER_ITEMS_TTL = Duration.ofMinutes(30);

    @Observed(name = "cache.orderItems.get", contextualName = "상품목록-캐시조회")
    public List<OrderItem> getOrderItems(String orderNo) {
        // 로컬 캐시
        List<OrderItem> localCached = orderItemsLocalCache.getIfPresent(orderNo);
        if (localCached != null) {
            log.info("상품목록 로컬 캐시 히트: {}", orderNo);
            return localCached;
        }

        // Redis 캐시
        Object redisCached = redisTemplate.opsForValue().get(ORDER_ITEMS_KEY + orderNo);
        if (redisCached instanceof List<?> list) {
            List<OrderItem> items = list.stream()
                    .map(item -> objectMapper.convertValue(item, OrderItem.class))
                    .toList();

            orderItemsLocalCache.put(orderNo, items);
            log.info("상품목록 Redis 캐시 히트: {}", orderNo);
            return items;
        }

        // DB 조회 후 캐싱
        List<OrderItem> items = orderRepository.findOrderItems(orderNo);
        log.info("상품목록 DB 조회: {}", orderNo);
        cacheOrderItems(orderNo, items);
        return items;
    }

    @Observed(name = "cache.orderItems.get", contextualName = "상품목록-캐시 생성")
    public void cacheOrderItems(String orderNo, List<OrderItem> items) {
        orderItemsLocalCache.put(orderNo, items);
        redisTemplate.opsForValue().set(ORDER_ITEMS_KEY + orderNo, items, ORDER_ITEMS_TTL);
        log.info("상품목록 캐시 저장: {}", orderNo);
    }
}