package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.InventoryOutboxEventRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRedisAtomicService {

    private static final String STOCK_KEY_PREFIX = "inventory:stock:option:";

    @Qualifier("inventoryStringRedisTemplate")
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductOptionRepository productOptionRepository;
    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;

    @Qualifier("atomicDecreaseStockScript")
    private final RedisScript<Long> atomicDecreaseStockScript;

    @Qualifier("atomicIncreaseStockScript")
    private final RedisScript<Long> atomicIncreaseStockScript;

    public void decreaseStock(List<Long> optionIds, Map<Long, Integer> quantityMap) {
        applyAtomic(optionIds, quantityMap, atomicDecreaseStockScript, true);
    }

    public void increaseStock(List<Long> optionIds, Map<Long, Integer> quantityMap) {
        applyAtomic(optionIds, quantityMap, atomicIncreaseStockScript, false);
    }

    private void applyAtomic(
            List<Long> optionIds,
            Map<Long, Integer> quantityMap,
            RedisScript<Long> script,
            boolean failOnInsufficient
    ) {
        List<Long> normalizedOptionIds = normalizeOptionIds(optionIds, quantityMap);

        List<Integer> quantities = buildQuantities(normalizedOptionIds, quantityMap);

        List<String> keys = normalizedOptionIds.stream()
                .map(this::toStockKey)
                .toList();

        String[] quantityArgs = quantities.stream()
                .map(String::valueOf)
                .toArray(String[]::new);

        ensureStockKeysLoaded(normalizedOptionIds, keys);
        Long result = execute(script, keys, quantityArgs);

        if (result != null && result == 1L) {
            return;
        }

        // 키 누락이면 DB로 보강 후 1회 재시도
        if (result != null && result == -1L) {
            ensureStockKeysLoaded(normalizedOptionIds, keys);
            result = execute(script, keys, quantityArgs);
            if (result != null && result == 1L) {
                return;
            }
        }

        if (failOnInsufficient && result != null && result == 0L) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Redis 원자 재고 처리에 실패했습니다.");
    }

    private Long execute(RedisScript<Long> script, List<String> keys, String[] args) {
        try {
            return stringRedisTemplate.execute(script, keys, (Object[]) args);
        } catch (Exception e) {
            log.error("Redis Lua 실행 실패: keys={}", keys, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "재고 저장소 연결에 실패했습니다.");
        }
    }

    private void ensureStockKeysLoaded(List<Long> optionIds, List<String> keys) {
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.size() != keys.size()) {
            hydrateStockKeys(optionIds);
            return;
        }

        boolean hasMissing = values.stream().anyMatch(Objects::isNull);
        if (hasMissing) {
            hydrateStockKeys(optionIds);
        }
    }

    private void hydrateStockKeys(List<Long> optionIds) {
        List<ProductOption> productOptions = productOptionRepository.findByProductOptionIdIn(optionIds);
        if (productOptions.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        Map<Long, Integer> stockMap = new HashMap<>();
        for (ProductOption productOption : productOptions) {
            Integer stock = productOption.getStockQuantity();
            if (stock == null) {
                throw new BusinessException(ErrorCode.INVENTORY_NOT_AVAILABLE);
            }
            stockMap.put(productOption.getProductOptionId(), stock);
        }

        // Redis가 유실된 경우 DB 스냅샷 + 미처리 Outbox 델타로 재구성한다.
        Map<Long, Integer> unprocessedDeltaMap =
                inventoryOutboxEventRepository.aggregateUnprocessedDeltaByOptionIds(optionIds);

        for (Long optionId : optionIds) {
            Integer baseStock = stockMap.get(optionId);
            if (baseStock == null) {
                throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
            }

            int delta = unprocessedDeltaMap.getOrDefault(optionId, 0);
            long reconstructed = (long) baseStock + delta;
            if (reconstructed < 0) {
                log.warn("재고 재구성 결과 음수 보정: optionId={}, baseStock={}, delta={}",
                        optionId, baseStock, delta);
                reconstructed = 0;
            }

            stringRedisTemplate.opsForValue().setIfAbsent(toStockKey(optionId), String.valueOf(reconstructed));
        }
    }

    private List<Long> normalizeOptionIds(List<Long> optionIds, Map<Long, Integer> quantityMap) {
        List<Long> target = optionIds;
        if (target == null || target.isEmpty()) {
            target = quantityMap == null ? List.of() : quantityMap.keySet().stream().toList();
        }
        List<Long> normalized = target.stream()
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }
        return normalized;
    }

    private List<Integer> buildQuantities(List<Long> optionIds, Map<Long, Integer> quantityMap) {
        if (quantityMap == null || quantityMap.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        return optionIds.stream().map(optionId -> {
            Integer quantity = quantityMap.get(optionId);
            if (quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
            }
            return quantity;
        }).toList();
    }

    private String toStockKey(Long optionId) {
        return STOCK_KEY_PREFIX + optionId;
    }
}
