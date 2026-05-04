package com.mudosa.musinsa.order.domain.repository;

import com.mudosa.musinsa.order.application.dto.OrderDetail;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.application.dto.QOrderItem;
import com.mudosa.musinsa.order.application.dto.QOrderDetail;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.mudosa.musinsa.brand.domain.model.QBrand.brand;
import static com.mudosa.musinsa.order.domain.model.QOrder.order;
import static com.mudosa.musinsa.order.domain.model.QOrderProduct.orderProduct;
import static com.mudosa.musinsa.product.domain.model.QImage.image;
import static com.mudosa.musinsa.product.domain.model.QProduct.product;
import static com.mudosa.musinsa.product.domain.model.QProductOption.productOption;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Observed(name = "repository.findOrderItems", contextualName = "주문 상품 조회")
    @Override
    public List<OrderItem> findOrderItems(String orderNo) {
        return queryFactory
                .select(new QOrderItem(
                        productOption.productOptionId,
                        brand.nameKo,
                        product.productName,
                        productOption.productPrice.amount,
                        orderProduct.productQuantity,
                        createThumbnailImageSubquery()
                ))
                .from(order)
                .join(order.orderProducts, orderProduct)
                .join(orderProduct.productOption, productOption)
                .join(productOption.product, product)
                .join(product.brand, brand)
                .where(order.orderNo.eq(orderNo))
                .fetch();
    }


    @Override
    public long countOrdersByUser(Long userId) {
        Long count = queryFactory
                .select(order.count())
                .from(order)
                .where(order.userId.eq(userId))
                .fetchOne();
        return count != null ? count : 0L;
    }

    @Observed(name = "repository.findOrderDetailsPaged", contextualName = "주문 목록 조회")
    @Override
    public List<OrderDetail> findOrderDetailsPaged(Long userId, Pageable pageable) {
        List<Long> orderIds = queryFactory
                .select(order.id)
                .from(order)
                .where(order.userId.eq(userId))
                .orderBy(order.registeredAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }

        return  queryFactory
                .select(new QOrderDetail(
                        order.orderNo,
                        order.status,
                        order.registeredAt,
                        order.totalPrice.amount,
                        productOption.productOptionId,
                        brand.nameKo,
                        product.productName,
                        productOption.productPrice.amount,
                        orderProduct.productQuantity,
                        createThumbnailImageSubquery()
                ))
                .from(order)
                .join(order.orderProducts, orderProduct)
                .join(orderProduct.productOption, productOption)
                .join(productOption.product, product)
                .join(product.brand, brand)
                .where(order.id.in(orderIds))
                .orderBy(order.registeredAt.desc())
                .fetch();
    }


    @Override
    public long countOrdersByStatusAndDateRange(OrderStatus status, LocalDateTime from, LocalDateTime to) {
        Long count = queryFactory
                .select(order.count())
                .from(order)
                .where(
                        statusEq(status),
                        registeredAtBetween(from, to)
                )
                .fetchOne();
        return count != null ? count : 0L;
    }

    @Observed(name = "repository.findOrderDetailsByDateRangePaged", contextualName = "관리자 주문 조회")
    @Override
    public List<OrderDetail> findOrderDetailsByStatusAndDateRangePaged(
            OrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {

        List<Long> orderIds = queryFactory
                .select(order.id)
                .from(order)
                .where(
                        statusEq(status),
                        registeredAtBetween(from, to)
                )
                .orderBy(order.registeredAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }

        return queryFactory
                .select(new QOrderDetail(
                        order.orderNo,
                        order.status,
                        order.registeredAt,
                        order.totalPrice.amount,
                        productOption.productOptionId,
                        brand.nameKo,
                        product.productName,
                        productOption.productPrice.amount,
                        orderProduct.productQuantity,
                        createThumbnailImageSubquery()
                ))
                .from(order)
                .join(order.orderProducts, orderProduct)
                .join(orderProduct.productOption, productOption)
                .join(productOption.product, product)
                .join(product.brand, brand)
                .where(order.id.in(orderIds))
                .orderBy(order.registeredAt.desc())
                .fetch();
    }

    private JPQLQuery<String> createThumbnailImageSubquery() {
        return JPAExpressions
                .select(image.imageUrl)
                .from(image)
                .where(image.product.eq(product)
                        .and(image.isThumbnail.eq(true)))
                .orderBy(image.imageId.asc())
                .limit(1);
    }

    private BooleanExpression statusEq(OrderStatus status) {
        return status != null ? order.status.eq(status) : null;
    }

    private BooleanExpression registeredAtBetween(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            return order.registeredAt.goe(from).and(order.registeredAt.lt(to));
        }
        if (from != null) {
            return order.registeredAt.goe(from);
        }
        if (to != null) {
            return order.registeredAt.lt(to);
        }
        return null;
    }
}
