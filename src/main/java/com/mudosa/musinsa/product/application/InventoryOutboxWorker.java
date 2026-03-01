package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;
import com.mudosa.musinsa.product.domain.repository.InventoryOutboxEventRepository;
import com.mudosa.musinsa.product.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${inventory.stock-strategy:redis_atomic_outbox}' == 'redis_atomic_outbox' " +
        "and '${inventory.outbox.worker.enabled:true}' == 'true'"
)
public class InventoryOutboxWorker {

    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;
    private final InventoryRepository inventoryRepository;

    @Value("${inventory.outbox.batch-size:200}")
    private int batchSize;

    @Value("${inventory.outbox.retry.max-retries:5}")
    private int maxRetries;

    @Value("${inventory.outbox.retry.initial-delay-millis:500}")
    private long retryInitialDelayMillis;

    @Value("${inventory.outbox.retry.max-delay-millis:30000}")
    private long retryMaxDelayMillis;

    @Scheduled(fixedDelayString = "${inventory.outbox.worker-delay-millis:200}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncInventoryOutbox() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> eventIds = inventoryOutboxEventRepository.findReadyEventIds(
                batchSize,
                now
        );

        if (eventIds.isEmpty()) {
            return;
        }

        for (Long eventId : eventIds) {
            try {
                processOne(eventId, now);
            } catch (Exception e) {
                log.error("[InventoryOutboxWorker] outbox 처리 중 예외: eventId={}", eventId, e);
            }
        }
    }

    private void processOne(Long eventId, LocalDateTime now) {
        InventoryOutboxEvent event = inventoryOutboxEventRepository.findByIdForUpdate(eventId)
                .orElse(null);

        if (event == null || !event.isReadyToProcess(now)) {
            return;
        }

        int delta = event.getEventType().toDelta(event.getQuantity());
        try {
            int updatedRows = inventoryRepository.applyStockChangeByProductOptionId(
                    event.getProductOptionId(),
                    delta
            );

            if (updatedRows == 1) {
                event.markProcessed();
                return;
            }

            handleRetryOrFail(
                    event,
                    "DB 재고 반영 실패(productOptionId=" + event.getProductOptionId() + ", delta=" + delta + ")"
            );
        } catch (Exception e) {
            handleRetryOrFail(
                    event,
                    "예외 발생으로 재시도 예약(productOptionId=" + event.getProductOptionId() + ", delta=" + delta + ", reason=" + e.getClass().getSimpleName() + ")"
            );
            log.warn("[InventoryOutboxWorker] DB 재고 반영 예외: eventId={}, productOptionId={}, delta={}",
                    event.getInventoryOutboxEventId(), event.getProductOptionId(), delta, e);
        }
    }

    private void handleRetryOrFail(InventoryOutboxEvent event, String reason) {
        int nextRetryCount = event.getRetryCount() + 1;
        if (nextRetryCount > maxRetries) {
            event.markFailed(reason + ", maxRetriesExceeded=" + maxRetries);
            log.error("[InventoryOutboxWorker] 최종 실패 처리: eventId={}, retryCount={}, maxRetries={}",
                    event.getInventoryOutboxEventId(), event.getRetryCount(), maxRetries);
            return;
        }

        long delayMillis = computeBackoffDelayMillis(nextRetryCount);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(delayMillis * 1_000_000);
        event.markRetryScheduled(reason, nextRetryAt);
        log.warn("[InventoryOutboxWorker] 재시도 예약: eventId={}, retryCount={}, nextRetryAt={}",
                event.getInventoryOutboxEventId(), event.getRetryCount(), nextRetryAt);
    }

    private long computeBackoffDelayMillis(int retryCount) {
        int exponent = Math.min(Math.max(0, retryCount - 1), 30);
        long delay = retryInitialDelayMillis * (1L << exponent);
        return Math.min(delay, retryMaxDelayMillis);
    }
}
