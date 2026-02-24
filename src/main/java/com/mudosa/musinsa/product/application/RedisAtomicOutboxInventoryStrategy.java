package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "inventory.stock-strategy",
        havingValue = "redis_atomic_outbox",
        matchIfMissing = true
)
public class RedisAtomicOutboxInventoryStrategy implements InventoryStockStrategy {

    private final InventoryRedisAtomicService inventoryRedisAtomicService;
    private final InventoryOutboxService inventoryOutboxService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void decreaseStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        applyStockChange(orderNo, optionIds, quantityMap, InventoryOutboxEventType.DECREASE);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        applyStockChange(orderNo, optionIds, quantityMap, InventoryOutboxEventType.INCREASE);
    }

    private void applyStockChange(
            String orderNo,
            List<Long> optionIds,
            Map<Long, Integer> quantityMap,
            InventoryOutboxEventType eventType
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("재고 변경은 트랜잭션 내부에서만 수행되어야 합니다.");
        }

        if (eventType == InventoryOutboxEventType.DECREASE) {
            inventoryRedisAtomicService.decreaseStock(optionIds, quantityMap);
            registerRollbackCompensation(orderNo, optionIds, quantityMap, InventoryOutboxEventType.INCREASE);
        } else {
            inventoryRedisAtomicService.increaseStock(optionIds, quantityMap);
            registerRollbackCompensation(orderNo, optionIds, quantityMap, InventoryOutboxEventType.DECREASE);
        }

        inventoryOutboxService.appendEvents(orderNo, quantityMap, eventType);
        log.info("[Inventory][RedisAtomicOutbox] 재고 변경 완료: orderNo={}, eventType={}, size={}",
                orderNo, eventType, quantityMap.size());
    }

    private void registerRollbackCompensation(
            String orderNo,
            List<Long> optionIds,
            Map<Long, Integer> quantityMap,
            InventoryOutboxEventType compensationType
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }

                try {
                    if (compensationType == InventoryOutboxEventType.INCREASE) {
                        inventoryRedisAtomicService.increaseStock(optionIds, quantityMap);
                    } else {
                        inventoryRedisAtomicService.decreaseStock(optionIds, quantityMap);
                    }
                    log.warn("[Inventory][RedisAtomicOutbox] 롤백 보상 완료: orderNo={}, compensationType={}",
                            orderNo, compensationType);
                } catch (Exception e) {
                    log.error("[Inventory][RedisAtomicOutbox] 롤백 보상 실패: orderNo={}, compensationType={}",
                            orderNo, compensationType, e);
                }
            }
        });
    }
}
