package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.common.lock.DistributedMultiLock;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.InsufficientStockItem;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.stock-strategy", havingValue = "redisson_lock")
public class RedissonLockInventoryStrategy implements InventoryStockStrategy {

    private final ProductOptionRepository productOptionRepository;

    @Override
    @DistributedMultiLock(keys = "#optionIds")
    @Transactional(propagation = Propagation.MANDATORY)
    public void decreaseStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        List<ProductOption> productOptions = productOptionRepository.findByProductOptionIdIn(optionIds);
        if (productOptions.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        List<InsufficientStockItem> insufficientItems = new ArrayList<>();
        for (ProductOption option : productOptions) {
            Integer quantity = quantityMap.get(option.getProductOptionId());
            if (quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
            }

            if (!option.hasEnoughStock(quantity)) {
                insufficientItems.add(new InsufficientStockItem(
                        option.getProductOptionId(),
                        quantity,
                        option.getStockQuantity()
                ));
            } else {
                option.decreaseStock(quantity);
            }
        }

        if (!insufficientItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, insufficientItems);
        }

        log.info("[Inventory][RedissonLock] 재고 차감 완료: orderNo={}, items={}", orderNo, optionIds.size());
    }

    @Override
    @DistributedMultiLock(keys = "#optionIds")
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreStock(String orderNo, List<Long> optionIds, Map<Long, Integer> quantityMap) {
        List<ProductOption> productOptions = productOptionRepository.findByProductOptionIdIn(optionIds);
        if (productOptions.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        Map<Long, ProductOption> optionMap = productOptions.stream()
                .collect(Collectors.toMap(ProductOption::getProductOptionId, po -> po));

        for (Long optionId : optionIds) {
            ProductOption option = optionMap.get(optionId);
            if (option == null) {
                throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
            }

            Integer quantity = quantityMap.get(optionId);
            if (quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
            }

            option.restoreStock(quantity);
        }

        log.info("[Inventory][RedissonLock] 재고 복구 완료: orderNo={}, items={}", orderNo, optionIds.size());
    }
}
