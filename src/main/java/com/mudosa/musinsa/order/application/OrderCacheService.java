package com.mudosa.musinsa.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.mudosa.musinsa.order.application.dto.OrderCacheData;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private final OrderRepository orderRepository;
    private final Cache<String, OrderCacheData> orderLocalCache;
    private final Cache<String, List<OrderItem>> orderItemsLocalCache;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ORDER_KEY = "order:";
    private static final String ORDER_ITEMS_KEY = "orderItems:";

    private final ObjectMapper objectMapper;

    // Redis Hash 필드명
    private static final String FIELD_DATA = "data";
    private static final String FIELD_STATUS = "status";

    private static final Duration ORDER_TTL = Duration.ofMinutes(5);
    private static final Duration ORDER_ITEMS_TTL = Duration.ofMinutes(30);

    /* 주문 캐시 조회  */
    @Observed(name = "cache.pendingOrder.get", contextualName = "주문서-주문-캐시조회")
    public OrderCacheData getOrder(String orderNo) {
        // 로컬 캐시
        OrderCacheData localCached = orderLocalCache.getIfPresent(orderNo);
        if (localCached != null) {
            log.info("주문서 주문 로컬 캐시 히트: {}", orderNo);
            return localCached;
        }

        // Redis 캐시
        Map<Object, Object> redisData = redisTemplate.opsForHash().entries(ORDER_KEY + orderNo);
        if (!redisData.isEmpty()) {
            OrderCacheData cacheData = convertFromRedisHash(redisData);
            orderLocalCache.put(orderNo, cacheData);
            log.info("주문 Redis 캐시 히트: {}", orderNo);
            return cacheData;
        }

        // DB 조회
        log.info("주문 DB 조회: {}", orderNo);
        return orderRepository.findByOrderNo(orderNo)
                .map(OrderCacheData::from)
                .orElse(null);
    }

    /* 주문 캐시 */
    @Observed(name = "cache.order.put", contextualName = "주문-캐시저장")
    public void cacheOrder(String orderNo, Order order) {
        OrderCacheData cacheData = OrderCacheData.from(order);

        // 로컬 캐시
        orderLocalCache.put(orderNo, cacheData);

        // Redis Hash 저장
        String redisKey = ORDER_KEY + orderNo;
        redisTemplate.opsForHash().put(redisKey, FIELD_DATA, cacheData);
        redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, cacheData.getStatus().name());
        redisTemplate.expire(redisKey, ORDER_TTL);

        log.info("주문 캐시 저장: {}", orderNo);
    }

    /* 주문 상태 변경 */
    @Observed(name = "cache.order.updateStatus", contextualName = "주문-상태-업데이트")
    public void updateOrderStatus(String orderNo, OrderStatus newStatus) {
        OrderCacheData localCached = orderLocalCache.getIfPresent(orderNo);
        if (localCached != null) {
            orderLocalCache.put(orderNo, localCached.withStatus(newStatus));
            log.info("로컬 캐시 상태 업데이트: {} → {}", orderNo, newStatus);
        }

        String redisKey = ORDER_KEY + orderNo;

        Object dataObj = redisTemplate.opsForHash().get(redisKey, FIELD_DATA);

        if (dataObj != null) {
            try {
                OrderCacheData existingData = objectMapper.convertValue(dataObj, OrderCacheData.class);

                OrderCacheData updatedData = existingData.withStatus(newStatus);

                Map<String, Object> updates = new HashMap<>();
                updates.put(FIELD_STATUS, newStatus.name());
                updates.put(FIELD_DATA, updatedData);

                redisTemplate.opsForHash().putAll(redisKey, updates);

                log.info("Redis 캐시 상태 업데이트: {} → {}", orderNo, newStatus);
            } catch (IllegalArgumentException e) {
                log.error("Redis 데이터 역직렬화 오류 발생: {}", orderNo, e);
            }
        } else {
            log.warn("Redis에서 주문 데이터를 찾을 수 없어 업데이트를 건너뜁니다: {}", orderNo);
        }
    }

    /* 상품 목록 캐시 조회 */
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
            @SuppressWarnings("unchecked")
            List<OrderItem> items = (List<OrderItem>) list;
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

    /* 상품 목록 조회 캐시 */
    public void cacheOrderItems(String orderNo, List<OrderItem> items) {
        orderItemsLocalCache.put(orderNo, items);
        redisTemplate.opsForValue().set(ORDER_ITEMS_KEY + orderNo, items, ORDER_ITEMS_TTL);
        log.info("상품목록 캐시 저장: {}", orderNo);
    }

    private OrderCacheData convertFromRedisHash(Map<Object, Object> redisData) {
        Object dataObj = redisData.get(FIELD_DATA);

        if (dataObj == null) {
            return null;
        }

        try {
            OrderCacheData cacheData = objectMapper.convertValue(dataObj, OrderCacheData.class);

            // 상태값 동기화 로직은 그대로 유지
            Object statusObj = redisData.get(FIELD_STATUS);
            if (statusObj != null) {
                // Enum 값 변환 시 toString()이 안전함
                OrderStatus status = OrderStatus.valueOf(statusObj.toString());
                if (cacheData.getStatus() != status) {
                    return cacheData.withStatus(status);
                }
            }
            return cacheData;

        } catch (IllegalArgumentException e) {
            log.error("Redis Hash 데이터를 OrderCacheData로 변환 실패", e);
            return null;
        }
    }
}