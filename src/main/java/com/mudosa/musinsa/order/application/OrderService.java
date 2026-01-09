package com.mudosa.musinsa.order.application;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.common.lock.DistributedMultiLock;
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
import com.mudosa.musinsa.order.domain.model.OrderStatus;
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

import java.util.ArrayList;
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
    private final OrderCacheService orderCacheService;
    private final InventoryService inventoryService;

    @Observed(name = "order.create", contextualName = "주문-생성")
    @Transactional
    public OrderCreateResponse createPendingOrder(OrderCreateRequest request, Long userId) {

        //ProductOption 매핑 & 주문 상품 유효성 확인
        Map<ProductOption, Integer> optionsWithQuantity = getProductOptionIntegerMap(request);

        //재고 확인
        validateStock(optionsWithQuantity);

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
        List<OrderItem> orderItems = buildOrderItems(optionsWithQuantity);
        orderCacheService.cacheOrderItems(savedOrder.getOrderNo(), orderItems);

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
        List<OrderItem> orderProductsInfo = orderCacheService.getOrderItems(orderNo);

        return new PendingOrderResponse(
                orderNo,
                order.getTotalPrice().getAmount(),
                order.getTotalDiscount().getAmount(),
                orderProductsInfo,
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
        List<OrderItem> orderProductsInfo = orderCacheService.getOrderItems(orderNo);

        //결제 정보 조회
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(()->new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

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

    @Observed(name = "order.complete", contextualName = "주문-완료")
    @Transactional
    public Long completeOrder(String orderNo) {
        //주문 조회
        Order order = orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> cacheData = orderCacheService.getOrderItems(orderNo);

        if (cacheData == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

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

        List<OrderFlatDto> flatList = orderRepository.findFlatOrderListWithDetails(userId);

        Map<String, List<OrderFlatDto>> groupedByOrderNo = flatList.stream()
                .collect(Collectors.groupingBy(OrderFlatDto::getOrderNo));

        List<OrderInfo> resultList = groupedByOrderNo.entrySet().stream()
                .map(entry -> {
                    String orderNo = entry.getKey();
                    List<OrderFlatDto> orderItemsFlat = entry.getValue();

                    List<OrderItem> items = orderItemsFlat.stream()
                            .map(flatDto -> new OrderItem(
                                    flatDto.getProductOptionId(),
                                    flatDto.getBrandName(),
                                    flatDto.getProductName(),
                                    flatDto.getItemAmount(),
                                    flatDto.getQuantity(),
                                    flatDto.getImageUrl(),
                                    flatDto.getSize(),
                                    flatDto.getColor()
                            ))
                            .collect(Collectors.toList());

                    OrderFlatDto firstFlatDto = orderItemsFlat.get(0);

                    return new OrderInfo(
                            orderNo,
                            firstFlatDto.getOrderStatus(),
                            firstFlatDto.getRegisteredAt(),
                            firstFlatDto.getTotalPrice(),
                            items
                    );
                })
                .collect(Collectors.toList());

        return new OrderListResponse(resultList);
    }



    @Observed(name = "order.cancel", contextualName = "주문-취소")
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.restoreStock(orderProduct.getProductQuantity());
        }

        order.cancel();
        return orderRepository.save(order);
    }

    @Observed(name = "order.rollbackCancel", contextualName = "주문-취소-롤백")
    public Order rollbackOrderCancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.decreaseStock(orderProduct.getProductQuantity());
        }

        order.rollbackToCompleted();
        return orderRepository.save(order);
    }

    @Observed(name = "order.deleteCartItems", contextualName = "장바구니-삭제")
    public Order deleteCartItems(Long orderId, Long userId) {
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

        return order;
    }

    @Observed(name = "order.rollback", contextualName = "주문-롤백")
    @Transactional
    public Order rollbackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            ProductOption productOption = productOptionRepository.findById(
                    orderProduct.getProductOption().getProductOptionId()
            ).orElseThrow();

            productOption.restoreStock(orderProduct.getProductQuantity());
        }

        order.rollbackStatus();

        return orderRepository.save(order);
    }


    @Observed(name = "order.validateStock", contextualName = "재고-검증")
    public void validateStock(Map<ProductOption, Integer> optionsWithQuantity) {
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
    }

    @Observed(name = "order.mapProductOptions", contextualName = "상품옵션-매핑")
    public Map<ProductOption, Integer> getProductOptionIntegerMap(OrderCreateRequest request) {
        List<Long> optionIds = request.getItems().stream()
                .map(OrderCreateItem::getProductOptionId)
                .toList();

        //상품 옵션 조회
        List<ProductOption> productOptions =
                productOptionRepository.findByProductOptionIdIn(optionIds);

        //상품 옵션 Id 유효성 확인
        if(productOptions.size() != optionIds.size()){
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        List<Long> list = productOptions.stream().filter(po -> !po.getProduct().getIsAvailable()).map(ProductOption::getProductOptionId).toList();

        //주문 상품 유효성 확인
        if(!list.isEmpty()){
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_ORDER, list);
        }

        Map<Long, Integer> quantityMap = request.getItems().stream()
                .collect(Collectors.toMap(
                        OrderCreateItem::getProductOptionId,
                        OrderCreateItem::getQuantity
                ));

        return productOptions.stream()
                .collect(Collectors.toMap(
                        option -> option,
                        option -> quantityMap.get(option.getProductOptionId())
                ));
    }

    @Observed(name = "order.buildOrderItems", contextualName = "캐싱 주문 목록 매핑")
    private List<OrderItem> buildOrderItems(Map<ProductOption, Integer> optionsWithQuantity) {
        return optionsWithQuantity.entrySet().stream()
                .map(entry -> {
                    ProductOption option = entry.getKey();
                    Integer quantity = entry.getValue();
                    Product product = option.getProduct();
                    Brand brand = product.getBrand();

                    String imageUrl = product.getImages().stream()
                            .filter(Image::getIsThumbnail)
                            .findFirst()
                            .map(Image::getImageUrl)
                            .orElse(null);

                    String size = null;
                    String color = null;
                    for (ProductOptionValue pov : option.getProductOptionValues()) {
                        String optionName = pov.getOptionValue().getOptionName();
                        if (ValueName.SIZE.getName().equals(optionName)) {
                            size = pov.getOptionValue().getOptionValue();
                        } else if (ValueName.COLOR.getName().equals(optionName)) {
                            color = pov.getOptionValue().getOptionValue();
                        }
                    }

                    return new OrderItem(
                            option.getProductOptionId(),
                            brand.getNameKo(),
                            product.getProductName(),
                            option.getProductPrice().getAmount(),
                            quantity,
                            imageUrl,
                            size,
                            color
                    );
                })
                .toList();
    }
}
