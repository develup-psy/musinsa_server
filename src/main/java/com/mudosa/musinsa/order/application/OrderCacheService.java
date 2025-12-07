package com.mudosa.musinsa.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.domain.model.Order;
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
    private final Cache<String, Order> pendingOrderLocalCache;
    private final Cache<String, Order> completedOrderLocalCache;
    private final Cache<String, List<OrderItem>> orderItemsLocalCache;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PENDING_ORDER_KEY = "pendingOrder:";
    private static final String COMPLETED_ORDER_KEY = "completedOrder:";
    private static final String ORDER_ITEMS_KEY = "orderItems:";

    private static final Duration ORDER_TTL = Duration.ofMinutes(5);
    private static final Duration ORDER_ITEMS_TTL = Duration.ofMinutes(30);

    /* 주문서 조회 캐싱 조회 */
    @Observed(name = "cache.pendingOrder.get", contextualName = "주문서-주문-캐시조회")
    public Order getPendingOrder(String orderNo) {
        // 로컬 캐시
        Order localCached = pendingOrderLocalCache.getIfPresent(orderNo);
        if (localCached != null) {
            log.info("주문서 주문 로컬 캐시 히트: {}", orderNo);
            return localCached;
        }

        // Redis 캐시
        Object redisCached = redisTemplate.opsForValue().get(PENDING_ORDER_KEY + orderNo);
        if (redisCached != null) {
            Order order = objectMapper.convertValue(redisCached, Order.class);
            pendingOrderLocalCache.put(orderNo, order);
            log.info("주문서 주문 Redis 캐시 히트: {}", orderNo);
            return order;
        }

        // DB 조회
        log.info("주문서 주문 DB 조회: {}", orderNo);
        return orderRepository.findByOrderNo(orderNo).orElse(null);
    }

    /* 주문서 조회용 주문 데이터 캐시 */
    public void cachePendingOrder(String orderNo, Order order) {
        pendingOrderLocalCache.put(orderNo, order);
        redisTemplate.opsForValue().set(PENDING_ORDER_KEY + orderNo, order, ORDER_TTL);
        log.info("주문서 조회 캐시 저장: {}", orderNo);
    }

    /* 상세 조회용 캐시 조회 */
    @Observed(name = "cache.completedOrder.get", contextualName = "상세-주문-캐시조회")
    public Order getCompletedOrder(String orderNo) {
        // 로컬 캐시 조회
        Order localCached = completedOrderLocalCache.getIfPresent(orderNo);
        if (localCached != null) {
            log.info("상세 주문 로컬 캐시 히트: {}", orderNo);
            return localCached;
        }

        // Redis 캐시 조회
        Object redisCached = redisTemplate.opsForValue().get(COMPLETED_ORDER_KEY + orderNo);
        if (redisCached != null) {
            Order order = objectMapper.convertValue(redisCached, Order.class);
            completedOrderLocalCache.put(orderNo, order);
            log.info("상세 주문 Redis 캐시 히트: {}", orderNo);
            return order;
        }

        // DB 조회
        log.info("상세 주문 DB 조회: {}", orderNo);
        return orderRepository.findByOrderNo(orderNo).orElse(null);
    }

    /* 상세조회용 주문 데이터 캐시 */
    @Observed(name = "cache.completedOrder.put", contextualName = "상세-주문-캐시저장")
    public void cacheCompletedOrder(String orderNo, Order order) {
        completedOrderLocalCache.put(orderNo, order);
        redisTemplate.opsForValue().set(COMPLETED_ORDER_KEY + orderNo, order, ORDER_TTL);
        log.info("상세 주문 캐시 저장: {}", orderNo);
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
        if (redisCached != null) {
            List<OrderItem> items = objectMapper.convertValue(redisCached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OrderItem.class));
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
}