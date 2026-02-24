package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;
import com.mudosa.musinsa.product.domain.model.InventoryOutboxEventType;
import com.mudosa.musinsa.product.domain.repository.InventoryOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryOutboxService {

    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendEvents(
            String orderNo,
            Map<Long, Integer> quantityMap,
            InventoryOutboxEventType eventType
    ) {
        if (quantityMap == null || quantityMap.isEmpty()) {
            return;
        }

        List<InventoryOutboxEvent> events = quantityMap.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(entry -> InventoryOutboxEvent.create(
                        orderNo,
                        entry.getKey(),
                        entry.getValue(),
                        eventType
                ))
                .toList();

        inventoryOutboxEventRepository.saveAll(events);
    }
}
