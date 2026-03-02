package com.mudosa.musinsa.product.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryStockStrategy inventoryStockStrategy;

    public void decreaseStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        inventoryStockStrategy.decreaseStock(orderNo, optionIds, quantityMap);
    }

    public void restoreStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        inventoryStockStrategy.restoreStock(orderNo, optionIds, quantityMap);
    }
}
