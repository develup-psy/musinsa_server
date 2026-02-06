package com.mudosa.musinsa.order.domain.repository;

import com.mudosa.musinsa.order.application.dto.OrderDetail;
import com.mudosa.musinsa.order.application.dto.OrderItem;

import java.util.List;

public interface OrderRepositoryCustom {
    List<OrderItem> findOrderItems(String orderNo);
    List<OrderDetail> findOrderDetails(Long userId);
}
