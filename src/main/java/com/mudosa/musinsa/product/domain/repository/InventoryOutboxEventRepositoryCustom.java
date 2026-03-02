package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryOutboxEventRepositoryCustom {

    List<Long> findReadyEventIds(int limit, LocalDateTime now);

    Optional<InventoryOutboxEvent> findByIdForUpdate(Long eventId);
}
