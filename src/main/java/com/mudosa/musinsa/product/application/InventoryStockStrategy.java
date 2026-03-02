package com.mudosa.musinsa.product.application;

import java.util.List;
import java.util.Map;

public interface InventoryStockStrategy {

    void decreaseStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap);

    void restoreStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap);
}
