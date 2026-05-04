package com.mudosa.musinsa.payment.domain.repository;

import com.mudosa.musinsa.payment.domain.model.PaymentQueueOutboxEvent;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mudosa.musinsa.payment.domain.model.QPaymentQueueOutboxEvent.paymentQueueOutboxEvent;

@Repository
@RequiredArgsConstructor
public class PaymentQueueOutboxEventRepositoryImpl implements PaymentQueueOutboxEventRepositoryCustom {

    private static final String FIND_READY_EVENT_IDS_SQL = """
            SELECT payment_queue_outbox_event_id
              FROM payment_queue_outbox_event
             WHERE status = 'PENDING'
               AND (next_retry_at IS NULL OR next_retry_at <= :now)
             ORDER BY payment_queue_outbox_event_id ASC
             FOR UPDATE SKIP LOCKED
            """;

    private final JPAQueryFactory jpaQueryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Long> findReadyEventIds(int limit, LocalDateTime now) {
        int safeLimit = Math.max(limit, 1);
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery(FIND_READY_EVENT_IDS_SQL)
                .setParameter("now", Timestamp.valueOf(now))
                .setMaxResults(safeLimit)
                .getResultList();

        return rows.stream()
                .map(Number::longValue)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PaymentQueueOutboxEvent> findByIdForUpdate(Long eventId) {
        PaymentQueueOutboxEvent event = jpaQueryFactory
                .selectFrom(paymentQueueOutboxEvent)
                .where(paymentQueueOutboxEvent.paymentQueueOutboxEventId.eq(eventId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(event);
    }

    @Override
    public Optional<PaymentQueueOutboxEvent> findByPaymentIdForUpdate(Long paymentId) {
        PaymentQueueOutboxEvent event = jpaQueryFactory
                .selectFrom(paymentQueueOutboxEvent)
                .where(paymentQueueOutboxEvent.paymentId.eq(paymentId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(event);
    }
}
