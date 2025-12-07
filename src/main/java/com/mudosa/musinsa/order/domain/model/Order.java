package com.mudosa.musinsa.order.domain.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.InsufficientStockItem;
import com.mudosa.musinsa.order.application.dto.OrderCreateItem;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.user.domain.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aspectj.weaver.ast.Or;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    private Long userId;

    private Long couponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderProduct> orderProducts;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus status;

    private String orderNo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_price"))
    private Money totalPrice;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_discount"))
    private Money totalDiscount;

    @CreatedDate
    private LocalDateTime registeredAt;

    private Boolean isSettleable;

    private LocalDateTime settledAt;

    @Column(name = "shipping_name")
    private String shippingName;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "shipping_phone")
    private String shippingPhone;

    @Builder
    private Order(Long userId, Long couponId, OrderStatus status, String orderNo, Money totalPrice, Money totalDiscount, LocalDateTime registeredAt, Boolean isSettleable, LocalDateTime settledAt, List<OrderProduct> orderProducts, String shippingName, String shippingAddress, String shippingPhone) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
        this.orderNo = orderNo;
        this.totalPrice = totalPrice;
        this.totalDiscount = Money.ZERO;
        this.registeredAt = registeredAt;
        this.isSettleable = isSettleable;
        this.settledAt = settledAt;
        this.orderProducts = orderProducts != null ? orderProducts : new ArrayList<>();
        this.shippingName = shippingName;
        this.shippingAddress = shippingAddress;
        this.shippingPhone = shippingPhone;
    }

    public static Order create(
            Long userId,
            Long couponId,
            Map<ProductOption, Integer> orderProductsWithQuantity,
            User user
    ) {

        if (orderProductsWithQuantity == null) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND, "상품 목록이 없는 주문은 생성할 수 없습니다");
        }

        //Order 생성
        Order order = Order.builder()
                .userId(userId)
                .orderNo(createOrderNumber())
                .status(OrderStatus.PENDING)
                .couponId(couponId)
                .orderProducts(new ArrayList<>())
                .shippingAddress(user.getCurrentAddress())
                .shippingName(user.getUserName())
                .shippingPhone(user.getContactNumber())
                .build();

        //총 가격 초기 세팅
        Money calculatedTotalPrice = Money.ZERO;

        for (Map.Entry<ProductOption, Integer> entry : orderProductsWithQuantity.entrySet()) {
            ProductOption productOption = entry.getKey();
            Integer quantity = entry.getValue();

            //OrderProduct 생성
            OrderProduct orderProduct = OrderProduct.create(order, productOption, quantity);
            order.orderProducts.add(orderProduct);

            //총 가격 계산
            calculatedTotalPrice = calculatedTotalPrice.add(
                    orderProduct.calculateItemPrice()
            );
        }

        order.totalPrice = calculatedTotalPrice;
        return order;
    }


    private static String createOrderNumber() {
        return "ORD" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public void complete() {
        this.status = this.status.complete();
        this.isSettleable = true;
    }

    public void rollbackStatus() {
        this.status = this.status.rollback();
        this.isSettleable = false;
    }

    public boolean isCancable() {
        return this.status.isCancable();
    }


    public void cancel() {
        this.status = this.status.cancel();
        this.isSettleable = false;
    }

    public boolean canFetchDetail() {
        return this.status.isCompleted() || this.status.isCancelled();
    }

    public void rollbackToCompleted() {
        this.status = this.status.rollback();
        this.isSettleable = true;
    }
}


