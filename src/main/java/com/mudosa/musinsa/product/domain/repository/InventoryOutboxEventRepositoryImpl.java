package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;
import com.mudosa.musinsa.product.domain.model.InventoryOutboxEventStatus;
import com.mudosa.musinsa.product.domain.model.InventoryOutboxEventType;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mudosa.musinsa.product.domain.model.QInventoryOutboxEvent.inventoryOutboxEvent;

@Repository
@RequiredArgsConstructor
public class InventoryOutboxEventRepositoryImpl implements InventoryOutboxEventRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<Long> findReadyEventIds(int limit, LocalDateTime now) {
        return jpaQueryFactory
                .select(inventoryOutboxEvent.inventoryOutboxEventId)
                .from(inventoryOutboxEvent)
                .where(
                        inventoryOutboxEvent.status.eq(InventoryOutboxEventStatus.PENDING)
                                .and(
                                        inventoryOutboxEvent.nextRetryAt.isNull()
                                                .or(inventoryOutboxEvent.nextRetryAt.loe(now))
                                )
                )
                .orderBy(inventoryOutboxEvent.inventoryOutboxEventId.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<InventoryOutboxEvent> findByIdForUpdate(Long eventId) {
        InventoryOutboxEvent event = jpaQueryFactory
                .selectFrom(inventoryOutboxEvent)
                .where(inventoryOutboxEvent.inventoryOutboxEventId.eq(eventId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(event);
    }

    @Override
    public Map<Long, Integer> aggregateUnprocessedDeltaByOptionIds(List<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return Map.of();
        }

        NumberExpression<Integer> signedQuantity = new CaseBuilder()
                .when(inventoryOutboxEvent.eventType.eq(InventoryOutboxEventType.DECREASE))
                .then(inventoryOutboxEvent.quantity.multiply(-1))
                .when(inventoryOutboxEvent.eventType.eq(InventoryOutboxEventType.INCREASE))
                .then(inventoryOutboxEvent.quantity)
                .otherwise(0);
        NumberExpression<Integer> deltaSum = signedQuantity.sum();

        List<Tuple> rows = jpaQueryFactory
                .select(inventoryOutboxEvent.productOptionId, deltaSum)
                .from(inventoryOutboxEvent)
                .where(
                        inventoryOutboxEvent.status.ne(InventoryOutboxEventStatus.PROCESSED)
                                .and(inventoryOutboxEvent.productOptionId.in(optionIds))
                )
                .groupBy(inventoryOutboxEvent.productOptionId)
                .fetch();

        Map<Long, Integer> result = new HashMap<>();
        for (Tuple row : rows) {
            Long optionId = row.get(inventoryOutboxEvent.productOptionId);
            Integer delta = row.get(deltaSum);
            if (optionId != null && delta != null) {
                result.put(optionId, delta);
            }
        }
        return result;
    }
}
