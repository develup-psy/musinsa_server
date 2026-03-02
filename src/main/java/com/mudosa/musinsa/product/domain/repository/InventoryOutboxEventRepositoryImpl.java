package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;
import com.mudosa.musinsa.product.domain.model.InventoryOutboxEventStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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
}
