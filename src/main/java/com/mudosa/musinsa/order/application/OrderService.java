package com.mudosa.musinsa.order.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.dto.*;
import com.mudosa.musinsa.order.application.dto.request.OrderCreateRequest;
import com.mudosa.musinsa.order.application.dto.response.OrderCreateResponse;
import com.mudosa.musinsa.order.application.dto.response.OrderDetailResponse;
import com.mudosa.musinsa.order.application.dto.response.OrderInfo;
import com.mudosa.musinsa.order.application.dto.response.OrderListResponse;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderProduct;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import com.mudosa.musinsa.product.application.InventoryService;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.CartItemRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;

    @Observed(name = "order.create", contextualName = "주문-생성")
    @Transactional
    public OrderCreateResponse createPendingOrder(OrderCreateRequest request, Long userId) {

        List<Long> optionIds = request.getItems().stream()
                .map(OrderCreateItem::getProductOptionId)
                .toList();

        //상품 옵션 조회
        List<ProductOption> productOptions =
                productOptionRepository.findByProductOptionIdIn(optionIds);

        //상품 옵션의 유효성 확인
        if(productOptions.size() != optionIds.size()){
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

        //재고 확인
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

        //사용자 조회
        User user = userRepository.findById(userId).orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        //주문 생성
        Order order = Order.create(
                userId,
                request.getCouponId(),
                optionsWithQuantity,
                user
        );

        Order savedOrder = orderRepository.save(order);

        //주문 상품 목록 캐싱
        return OrderCreateResponse.of(savedOrder.getId(), savedOrder.getOrderNo());
    }

    @Observed(name = "order.pending.fetch", contextualName = "주문서-조회")
    @Transactional(readOnly = true)
    public PendingOrderResponse fetchPendingOrder(String orderNo) {
        // 주문 조회
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 상품 목록 조회
        List<OrderItem> items = orderRepository.findOrderItems(orderNo);

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

    @Observed(name = "order.detail.fetch", contextualName = "주문-상세조회")
    public OrderDetailResponse fetchOrderDetail(String orderNo) {
        // 주문 조회
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if(!order.canFetchDetail()){
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        // 상품 목록 조회
        List<OrderItem> orderProductsInfo = orderRepository.findOrderItems(orderNo);

        //결제 정보 조회
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(()->new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        return OrderDetailResponse.build(order, payment, orderProductsInfo);
    }

    @Observed(name = "order.complete", contextualName = "주문-완료")
    @Transactional
    public Long completeOrder(String orderNo) {
        //주문 조회
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

        //일반 조회
        inventoryService.decreaseStock(optionIds, quantityMap);

        //주문 상태 변경
        order.complete();
        orderRepository.save(order);

        return order.getId();
    }

    @Transactional
    public void cancelPendingOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if(!order.isCancable()){
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_ORDER, "취소할 수 없는 상태입니다.");
        }

        orderRepository.delete(order);
    }

    @Transactional(readOnly = true)
    public OrderListResponse fetchOrderList(Long userId) {

        List<OrderDetail> orderList = orderRepository.findOrderDetails(userId);

        Map<String, List<OrderDetail>> groupedByOrderNo = orderList.stream()
                .collect(Collectors.groupingBy(OrderDetail::orderNo));

        List<OrderInfo> resultList = groupedByOrderNo.entrySet().stream()
                .map(entry -> {
                    String orderNo = entry.getKey();
                    List<OrderDetail> orderItemsFlat = entry.getValue();

                    List<OrderItem> items = orderItemsFlat.stream()
                            .map(OrderItem::toOrderItem)
                            .collect(Collectors.toList());

                    OrderDetail firstFlatDto = orderItemsFlat.getFirst();

                    return new OrderInfo(
                            orderNo,
                            firstFlatDto.orderStatus(),
                            firstFlatDto.registeredAt(),
                            firstFlatDto.totalPrice(),
                            items
                    );
                })
                .collect(Collectors.toList());

        return new OrderListResponse(resultList);
    }


    @Observed(name = "order.cancel", contextualName = "주문-취소")
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

    @Observed(name = "order.rollbackCancel", contextualName = "주문-취소-롤백")
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

    @Observed(name = "order.deleteCartItems", contextualName = "장바구니-삭제")
    public void deleteCartItems(Long orderId, Long userId) {
        //주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        //장바구니 삭제
        List<Long> productOptionIds = order.getOrderProducts().stream()
                .map(op -> op.getProductOption().getProductOptionId())
                .toList();

        cartItemRepository.deleteByUserIdAndProductOptionIdIn(
                userId,
                productOptionIds
        );
    }

    @Observed(name = "order.rollback", contextualName = "주문-롤백")
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
}
