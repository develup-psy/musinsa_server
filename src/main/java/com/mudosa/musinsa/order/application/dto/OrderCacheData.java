package com.mudosa.musinsa.order.application.dto;

import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCacheData {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalPrice;
    private BigDecimal totalDiscount;
    private String shippingName;
    private String shippingAddress;
    private String shippingPhone;
    private OrderStatus status;
    private LocalDateTime registeredAt;

    public static OrderCacheData from(Order order) {
        return OrderCacheData.builder()
                .orderId(order.getId())
                .registeredAt(order.getRegisteredAt())
                .orderNo(order.getOrderNo())
                .totalPrice(order.getTotalPrice().getAmount())
                .totalDiscount(order.getTotalDiscount().getAmount())
                .shippingName(order.getShippingName())
                .shippingAddress(order.getShippingAddress())
                .shippingPhone(order.getShippingPhone())
                .status(order.getStatus())
                .build();
    }

    public OrderCacheData withStatus(OrderStatus newStatus) {
        return OrderCacheData.builder()
                .orderId(this.orderId)
                .registeredAt(this.registeredAt)
                .orderNo(this.orderNo)
                .totalPrice(this.totalPrice)
                .totalDiscount(this.totalDiscount)
                .shippingName(this.shippingName)
                .shippingAddress(this.shippingAddress)
                .shippingPhone(this.shippingPhone)
                .status(newStatus)
                .build();
    }

    public boolean canFetchDetail() {
        return this.status == OrderStatus.COMPLETED || this.status == OrderStatus.CANCELLED;
    }
}
