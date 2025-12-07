package com.mudosa.musinsa.order.domain.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventOption;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderProduct extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_product_id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    @JsonIgnore
    private ProductOption productOption;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "product_price"))
    private Money productPrice;

    private Integer productQuantity;

    @Builder
    private OrderProduct(Order order, ProductOption productOption, Money productPrice, Integer productQuantity) {
        this.order = order;
        this.productOption = productOption;
        this.productPrice = productPrice;
        this.productQuantity = productQuantity;
    }

    public static OrderProduct create(Order order, ProductOption productOption, int productQuantity){
        validateQuantity(productQuantity);
        return OrderProduct.builder()
                .order(order)
                .productPrice(productOption.getProductPrice())
                .productOption(productOption)
                .productQuantity(productQuantity)
                .build();
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(
                    ErrorCode.ORDER_CREATE_FAIL,
                    "상품은 1개 이상 주문 가능합니다"
            );
        }
    }

    public Long getProductOptionId() {
        return productOption.getProductOptionId();  // productOption 객체의 ID 반환
    }

    public Money calculateItemPrice(){
        Money productPrice = productOption.getProductPrice();
        return productPrice.multiply(this.productQuantity);
    }

    public void setOrderForTest(Order order) {
        this.order = order;
    }

}
