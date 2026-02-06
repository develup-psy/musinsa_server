package com.mudosa.musinsa.order.application.dto.response;

import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Builder
@AllArgsConstructor
public class OrderDetailResponse {
    //주문 정보
    private String orderNo;
    private OrderStatus orderStatus;
    private BigDecimal totalProductAmount;
    private BigDecimal discountAmount;
    private LocalDateTime orderedAt;

    //사용자 정보
    private String userName;
    private String userAddress;
    private String userContactNumber;

    //상품 정보
    private List<OrderItem> orderItems;

    //결제 정보
    private String paymentTransactionId;
    private BigDecimal paymentFinalAmount;
    private String paymentMethod;
    private PgProvider pgProvider;
    private LocalDateTime approvedAt;
    private PaymentStatus paymentStatus;
    private LocalDateTime cancelledAt;

    public static OrderDetailResponse build(Order order, Payment payment, List<OrderItem> orderProductsInfo){
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .orderStatus(order.getStatus())
                .totalProductAmount(order.getTotalPrice().getAmount())
                .discountAmount(order.getTotalDiscount().getAmount())
                .orderedAt(order.getRegisteredAt())
                .userName(order.getShippingName())
                .userAddress(order.getShippingAddress())
                .userContactNumber(order.getShippingPhone())
                .orderItems(orderProductsInfo)
                .paymentFinalAmount(payment.getAmount())
                .paymentMethod(payment.getMethod())
                .pgProvider(payment.getPgProvider())
                .approvedAt(payment.getApprovedAt())
                .paymentStatus(payment.getStatus())
                .cancelledAt(payment.getCancelledAt())
                .paymentTransactionId(payment.getPgTransactionId())
                .build();
    }
}
