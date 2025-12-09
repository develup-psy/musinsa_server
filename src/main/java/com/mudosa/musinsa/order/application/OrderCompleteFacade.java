package com.mudosa.musinsa.order.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCompleteFacade {

    private final OrderService orderService;
    private final OrderCacheService orderCacheService;

    public Long completeOrder(String orderNo) {
        List<OrderItem> cacheData = orderCacheService.getOrderItems(orderNo);

        if (cacheData == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        List<Long> optionIds = cacheData.stream()
                .map(OrderItem::getProductOptionId)
                .toList();

        return orderService.completeOrder(orderNo, optionIds);
    }
}