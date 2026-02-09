package com.mudosa.musinsa.order.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.*;
import com.mudosa.musinsa.order.application.dto.request.OrderCreateRequest;
import com.mudosa.musinsa.order.application.dto.response.OrderCreateResponse;
import com.mudosa.musinsa.order.application.dto.response.OrderDetailResponse;
import com.mudosa.musinsa.order.application.dto.response.OrderInfo;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderProduct;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import com.mudosa.musinsa.product.application.InventoryService;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.CartItemRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionValueRepository;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;

    @Observed(name = "order.create", contextualName = "주문 생성")
    @Transactional
    public OrderCreateResponse createPendingOrder(OrderCreateRequest request, Long userId) {

        List<Long> optionIds = request.getItems().stream()
                .map(OrderCreateItem::getProductOptionId)
                .toList();

        List<ProductOption> productOptions =
                productOptionRepository.findByProductOptionIdIn(optionIds);

        if (productOptions.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        Map<Long, Integer> quantityMap = request.getItems().stream()
                .collect(Collectors.toMap(
                        OrderCreateItem::getProductOptionId,
                        OrderCreateItem::getQuantity
                ));

        Map<ProductOption, Integer> optionsWithQuantity = productOptions.stream()
                .collect(Collectors.toMap(
                        option -> option,
                        option -> quantityMap.get(option.getProductOptionId())
                ));

        List<InsufficientStockItem> insufficientItems = optionsWithQuantity.entrySet().stream()
                .filter(entry -> !entry.getKey().hasEnoughStock(entry.getValue()))
                .map(entry -> new InsufficientStockItem(
                        entry.getKey().getProductOptionId(),
                        entry.getValue(),
                        entry.getKey().getStockQuantity()
                ))
                .toList();

        if (!insufficientItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, insufficientItems);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Order order = Order.create(userId, request.getCouponId(), optionsWithQuantity, user);
        Order savedOrder = orderRepository.save(order);

        return OrderCreateResponse.of(savedOrder.getId(), savedOrder.getOrderNo());
    }

    @Observed(name = "order.pending.fetch", contextualName = "주문서 조회")
    @Transactional(readOnly = true)
    public PendingOrderResponse fetchPendingOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> items = orderRepository.findOrderItems(orderNo);
        applyOptionsToItems(items);

        return new PendingOrderResponse(
                orderNo,
                order.getTotalPrice().getAmount(),
                order.getTotalDiscount().getAmount(),
                items,
                order.getShippingName(),
                order.getShippingAddress(),
                order.getShippingPhone()
        );
    }

    @Observed(name = "order.detail.fetch", contextualName = "주문 상세 조회")
    public OrderDetailResponse fetchOrderDetail(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.canFetchDetail()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        List<OrderItem> orderProductsInfo = orderRepository.findOrderItems(orderNo);
        applyOptionsToItems(orderProductsInfo);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        return OrderDetailResponse.build(order, payment, orderProductsInfo);
    }

    @Observed(name = "order.complete", contextualName = "주문 완료")
    @Transactional
    public Long completeOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> cacheData = orderRepository.findOrderItems(orderNo);

        List<Long> optionIds = cacheData.stream()
                .map(OrderItem::getProductOptionId)
                .toList();

        Map<Long, Integer> quantityMap = order.getOrderProducts().stream()
                .collect(Collectors.toMap(
                        OrderProduct::getProductOptionId,
                        OrderProduct::getProductQuantity
                ));

        inventoryService.decreaseStock(optionIds, quantityMap);

        order.complete();
        orderRepository.save(order);

        return order.getId();
    }

    @Transactional
    public void cancelPendingOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.isCancable()) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_ORDER, "취소할 수 없는 상태입니다.");
        }

        orderRepository.delete(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> fetchOrderList(Long userId, Pageable pageable) {
        long totalCount = orderRepository.countOrdersByUser(userId);

        List<OrderDetail> orderDetails = orderRepository.findOrderDetailsPaged(userId, pageable);

        List<OrderInfo> orderInfos = groupAndApplyOptions(orderDetails);

        return new PageImpl<>(orderInfos, pageable, totalCount);
    }

    @Observed(name = "order.dateRange.fetch", contextualName = "기간 주문 목록 조회")
    @Transactional(readOnly = true)
    public Page<OrderInfo> fetchOrdersByStatusAndDateRange(
            OrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {

        long totalCount = orderRepository.countOrdersByStatusAndDateRange(status, from, to);

        List<OrderDetail> orderDetails = orderRepository.findOrderDetailsByStatusAndDateRangePaged(
                status, from, to, pageable
        );

        List<OrderInfo> orderInfos = groupAndApplyOptions(orderDetails);

        return new PageImpl<>(orderInfos, pageable, totalCount);
    }

    @Observed(name = "order.cancel", contextualName = "주문 취소")
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.restoreStock(orderProduct.getProductQuantity());
        }

        order.cancel();
    }

    @Observed(name = "order.rollbackCancel", contextualName = "주문 취소 롤백")
    public void rollbackOrderCancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.decreaseStock(orderProduct.getProductQuantity());
        }

        order.rollbackToCompleted();
    }

    @Observed(name = "order.deleteCartItems", contextualName = "장바구니 삭제")
    public void deleteCartItems(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        List<Long> productOptionIds = order.getOrderProducts().stream()
                .map(op -> op.getProductOption().getProductOptionId())
                .toList();

        cartItemRepository.deleteByUserIdAndProductOptionIdIn(userId, productOptionIds);
    }

    @Observed(name = "order.rollback", contextualName = "주문 롤백")
    @Transactional
    public void rollbackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.restoreStock(orderProduct.getProductQuantity());
        }

        order.rollbackStatus();
        orderRepository.save(order);
    }


    private List<OrderInfo> groupAndApplyOptions(List<OrderDetail> orderDetails) {
        Map<String, List<OrderDetail>> groupedByOrderNo = orderDetails.stream()
                .collect(Collectors.groupingBy(OrderDetail::orderNo));

        List<OrderInfo> resultList = groupedByOrderNo.entrySet().stream()
                .map(entry -> {
                    String orderNo = entry.getKey();
                    List<OrderDetail> items = entry.getValue();
                    OrderDetail first = items.getFirst();

                    List<OrderItem> orderItems = items.stream()
                            .map(OrderItem::toOrderItem)
                            .collect(Collectors.toList());

                    return new OrderInfo(
                            orderNo,
                            first.orderStatus(),
                            first.registeredAt(),
                            first.totalPrice(),
                            orderItems
                    );
                })
                .collect(Collectors.toList());

        // 모든 OrderItem에 옵션 일괄 적용
        List<OrderItem> allItems = resultList.stream()
                .flatMap(info -> info.getOrderItems().stream())
                .collect(Collectors.toList());
        applyOptionsToItems(allItems);

        return resultList;
    }


    private void applyOptionsToItems(List<OrderItem> items) {
        if (items.isEmpty()) return;

        List<Long> productOptionIds = items.stream()
                .map(OrderItem::getProductOptionId)
                .distinct()
                .toList();

        List<ProductOptionValue> allOptionValues =
                productOptionValueRepository.findAllByProductOptionIdsWithOptionValue(productOptionIds);

        Map<Long, Map<String, String>> optionMap = allOptionValues.stream()
                .collect(Collectors.groupingBy(
                        pov -> pov.getProductOption().getProductOptionId(),
                        Collectors.toMap(
                                pov -> pov.getOptionValue().getOptionName(),
                                pov -> pov.getOptionValue().getOptionValue(),
                                (v1, v2) -> v1
                        )
                ));

        items.forEach(item -> {
            Map<String, String> options = optionMap.getOrDefault(item.getProductOptionId(), Map.of());
            item.applyOptions(options);
        });
    }
}
