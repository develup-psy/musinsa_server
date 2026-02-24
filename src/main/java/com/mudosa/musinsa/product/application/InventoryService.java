package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.common.lock.DistributedMultiLock;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.InsufficientStockItem;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductOptionRepository productOptionRepository;

    @DistributedMultiLock(keys = "#optionIds")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseStock(List<Long> optionIds, Map<Long, Integer> quantityMap) {
        log.info("락 범위 시작");
        List<ProductOption> productOptions = productOptionRepository.findByProductOptionIdIn(optionIds);

        List<InsufficientStockItem> insufficientItems = new ArrayList<>();

        productOptions.forEach(po -> {
            Integer quantityToDeduct = quantityMap.get(po.getProductOptionId());
            if (!po.hasEnoughStock(quantityToDeduct)) {
                insufficientItems.add(new InsufficientStockItem(
                        po.getProductOptionId(),
                        quantityToDeduct,
                        po.getStockQuantity()
                ));
            } else {
                po.decreaseStock(quantityToDeduct);
            }
        });

        if (!insufficientItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, insufficientItems);
        }

        // 여기서 커밋 → 락 해제
        log.info("락 범위 종료 및 커밋");
    }
}
