package com.mudosa.musinsa.order.domain.repository;

import com.mudosa.musinsa.order.application.dto.OrderDetail;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepositoryCustom {

    List<OrderItem> findOrderItems(String orderNo);

    long countOrdersByUser(Long userId);
    List<OrderDetail> findOrderDetailsPaged(Long userId, Pageable pageable);

    long countOrdersByStatusAndDateRange(OrderStatus status, LocalDateTime from, LocalDateTime to);
    List<OrderDetail> findOrderDetailsByStatusAndDateRangePaged(OrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
