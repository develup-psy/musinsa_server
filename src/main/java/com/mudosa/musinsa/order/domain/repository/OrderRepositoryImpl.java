package com.mudosa.musinsa.order.domain.repository;

import com.mudosa.musinsa.order.application.dto.OrderFlatDto;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import com.mudosa.musinsa.order.application.dto.QOrderFlatDto;
import com.mudosa.musinsa.order.application.dto.QOrderItem;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.product.domain.model.ValueName;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.mudosa.musinsa.brand.domain.model.QBrand.brand;
import static com.mudosa.musinsa.order.domain.model.QOrder.order;
import static com.mudosa.musinsa.order.domain.model.QOrderProduct.orderProduct;
import static com.mudosa.musinsa.product.domain.model.QImage.image;
import static com.mudosa.musinsa.product.domain.model.QOptionValue.optionValue1;
import static com.mudosa.musinsa.product.domain.model.QProduct.product;
import static com.mudosa.musinsa.product.domain.model.QProductOption.productOption;
import static com.mudosa.musinsa.product.domain.model.QProductOptionValue.productOptionValue;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    @Observed(name = "repository.findOrderItems", contextualName = "주문상품-조회-QueryDSL")
    @Override
    public List<OrderItem> findOrderItems(String orderNo) {
        return queryFactory
                .select(createOrderItemProjection())
                .from(order)
                .join(order.orderProducts, orderProduct)
                .join(orderProduct.productOption, productOption)
                .join(productOption.product, product)
                .join(product.brand, brand)
                .leftJoin(productOption.productOptionValues, productOptionValue)
                .leftJoin(productOptionValue.optionValue, optionValue1)
                .where(order.orderNo.eq(orderNo))
                .groupBy(
                        productOption.productOptionId,
                        brand.nameKo,
                        product.productName,
                        productOption.productPrice.amount,
                        orderProduct.productQuantity
                )
                .fetch();
    }

    @Observed(name = "repository.findFlatOrderList", contextualName = "주문목록-조회-QueryDSL")
    @Override
    public List<OrderFlatDto> findFlatOrderListWithDetails(Long userId) {
        StringExpression sizeValue = createSizeValueExpression();
        StringExpression colorValue = createColorValueExpression();
        JPQLQuery<String> imageOne = createThumbnailImageSubquery();

        return queryFactory
                .select(new QOrderFlatDto(
                        order.orderNo,
                        order.status,
                        order.registeredAt,
                        order.totalPrice.amount,
                        productOption.productOptionId,
                        brand.nameKo,
                        product.productName,
                        productOption.productPrice.amount,
                        orderProduct.productQuantity,
                        imageOne,
                        sizeValue,
                        colorValue
                ))
                .from(order)
                .join(order.orderProducts, orderProduct)
                .join(orderProduct.productOption, productOption)
                .join(productOption.product, product)
                .join(product.brand, brand)
                .leftJoin(productOption.productOptionValues, productOptionValue)
                .leftJoin(productOptionValue.optionValue, optionValue1)
                .where(order.userId.eq(userId).and(order.status.ne(OrderStatus.PENDING)))
                .groupBy(
                        order.orderNo, order.status, order.registeredAt, order.totalPrice.amount,
                        productOption.productOptionId, brand.nameKo, product.productName,
                        productOption.productPrice.amount, orderProduct.productQuantity
                )
                .fetch();
    }

    private QOrderItem createOrderItemProjection() {
        return new QOrderItem(
                productOption.productOptionId,
                brand.nameKo,
                product.productName,
                productOption.productPrice.amount,
                orderProduct.productQuantity,
                createThumbnailImageSubquery(),
                createSizeValueExpression(),
                createColorValueExpression()
        );
    }

    private StringExpression createSizeValueExpression() {
        return new CaseBuilder()
                .when(optionValue1.optionName.eq(ValueName.SIZE.getName()))
                .then(optionValue1.optionValue)
                .otherwise((String) null)
                .max();
    }

    private StringExpression createColorValueExpression() {
        return new CaseBuilder()
                .when(optionValue1.optionName.eq(ValueName.COLOR.getName()))
                .then(optionValue1.optionValue)
                .otherwise((String) null)
                .max();
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
}
