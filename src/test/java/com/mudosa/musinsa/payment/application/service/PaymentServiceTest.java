package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.brand.domain.model.BrandStatus;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.order.domain.model.OrderProduct;
import com.mudosa.musinsa.order.domain.model.OrderStatus;
import com.mudosa.musinsa.order.domain.repository.OrderRepository;
import com.mudosa.musinsa.payment.application.dto.PaymentCreateDto;
import com.mudosa.musinsa.payment.application.dto.PaymentCreationResult;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.PaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentConfirmResponse;
import com.mudosa.musinsa.payment.domain.model.*;
import com.mudosa.musinsa.payment.domain.repository.PaymentLogRepository;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import com.mudosa.musinsa.product.domain.model.*;
import com.mudosa.musinsa.product.domain.repository.CartItemRepository;
import com.mudosa.musinsa.product.domain.repository.InventoryRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest extends ServiceConfig {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private PaymentLogRepository paymentLogRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @MockBean
    private PaymentProcessor paymentProcessor;

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @AfterEach
    void tearDown() {
        paymentLogRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
    }

    @DisplayName("결제를 승인하면 결제를 생성한다")
    @Test
    void createPayment(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(100);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        String orderNo = "ORD_001";
        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        PaymentCreateDto request = PaymentCreateDto.builder()
                .pgProvider(PgProvider.TOSS)
                .orderNo(orderNo)
                .totalAmount(new BigDecimal(20000L))
                .build();

        //when
        PaymentCreationResult result = paymentConfirmService.createPayment(request, user.getId());

        //then
        assertThat(result)
                .extracting("orderId", "userId")
                .containsExactly(order.getId(), user.getId());


        Payment payment = paymentRepository.findById(result.getPaymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount().compareTo(request.getTotalAmount())).isZero();

        PaymentLog paymentLog = paymentLogRepository.findByPaymentId(payment.getId());
        assertThat(paymentLog.getEventStatus()).isEqualTo(PaymentEventType.CREATED);

        Order completedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Inventory updatedInventory = inventoryRepository.findById(inventory.getInventoryId()).orElseThrow();
        assertThat(updatedInventory.getStockQuantity().getValue()).isEqualTo(98);
    }

    @DisplayName("존재하지 않은 주문이 넘어오면 결제 생성에 실패한다. ")
    @Test
    void createPaymentTransactionWithOrderNotFound(){
        //given
        User user = userRepository.save(createUser());
        PaymentCreateDto request = PaymentCreateDto.builder()
                .pgProvider(PgProvider.TOSS)
                .orderNo("NON_EXISTENT_ORDER")
                .totalAmount(new BigDecimal(20000L))
                .build();

        //when & then
        assertThatThrownBy(() -> paymentConfirmService.createPayment(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 주문입니다");
    }

    @DisplayName("재고가 부족하면 결제 생성에 실패한다. ")
    @Test
    void createPaymentTransactionWithInsufficientStock(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(1);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        String orderNo = "ORD_002";
        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        PaymentCreateDto request = PaymentCreateDto.builder()
                .pgProvider(PgProvider.TOSS)
                .orderNo(orderNo)
                .totalAmount(new BigDecimal(20000L))
                .build();

        //when & then
        assertThatThrownBy(() -> paymentConfirmService.createPayment(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("부족");

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).isEmpty();

        Inventory unchangedInventory = inventoryRepository.findById(inventory.getInventoryId()).orElseThrow();
        assertThat(unchangedInventory.getStockQuantity().getValue()).isEqualTo(1);

        Order unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("PG사 결제 승인시 장바구니가 삭제되고 결제 상태가 변경된다.")
    @Test
    void approvePaymentExecutesInCorrectOrder(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(100);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        String orderNo = "ORD_003";
        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        Order savedOrder = orderRepository.save(order);

        // 결제 생성
        Payment payment = Payment.create(
                savedOrder.getId(),
                new BigDecimal(20000L),
                PgProvider.TOSS,
                user.getId()
        );
        Payment savedPayment = paymentRepository.save(payment);

        // 장바구니 생성
        CartItem cartItem = createCartItem(user, productOption, 2);
        cartItemRepository.save(cartItem);

        // PG사 응답
        PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                .paymentKey("test_pg_tx_123")
                .orderNo(orderNo)
                .method("카드")
                .totalAmount(20000L)
                .pgProvider(PgProvider.TOSS.name())
                .approvedAt(LocalDateTime.now())
                .build();
        //when
        paymentConfirmService.approvePayment(savedPayment.getId(),user.getId(),paymentResponse,savedOrder.getId());

        //then
        List<CartItem> cartItems = cartItemRepository.findAllByUserId(user.getId());
        assertThat(cartItems).isEmpty();

        Payment updatedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(updatedPayment)
                .extracting("status", "pgTransactionId")
                .contains(PaymentStatus.APPROVED, "test_pg_tx_123");

    }

    @DisplayName("PG사 결제 승인시 장바구니가 삭제되고 결제 상태가 변경된다.")
    @Test
    void approvePaymentExecutesExcpetionWhenPaymentIdNotExists(){
        //given
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(100);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, 2);
        orderProducts.add(orderProduct);

        String orderNo = "ORD_004";
        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        Order savedOrder = orderRepository.save(order);

        // 결제 생성
        Payment payment = Payment.create(
                savedOrder.getId(),
                new BigDecimal(20000L),
                PgProvider.TOSS,
                user.getId()
        );
        Payment savedPayment = paymentRepository.save(payment);

        // 장바구니 생성
        CartItem cartItem = createCartItem(user, productOption, 2);
        cartItemRepository.save(cartItem);

        // PG사 응답
        PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                .paymentKey(null)
                .orderNo(orderNo)
                .method("카드")
                .totalAmount(20000L)
                .pgProvider(PgProvider.TOSS.name())
                .approvedAt(LocalDateTime.now())
                .build();
        //when
        assertThatThrownBy(()-> paymentConfirmService.approvePayment(savedPayment.getId(),user.getId(),paymentResponse,savedOrder.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("결제 PG 트랜잭션 ID가 유효하지 않습니다");
    }

    @DisplayName("주문 조회 실패 시 예외가 발생하고 복원할 것은 없다.")
    @Test
    void throwExceptionWhenOrderNotFoundInTx1(){
        //given
        User user = userRepository.save(createUser());

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo("NON_EXISTENT_ORDER")
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        // when & then
        assertThatThrownBy(() -> {
            paymentService.confirmPayment(request, user.getId());
        }).isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 주문입니다");

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).isEmpty();
    }

    @DisplayName("PG 승인 실패 시 재고가 복원된다")
    @Test
    void restoreStockWhenPgApprovalFails(){
        //given
        TestData testData = createTestData("ORD_PG_FAIL_001", 100, 2);

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, "PG사 승인 거부"));

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .paymentKey("test_key")
                .pgProvider(PgProvider.TOSS)
                .amount(20000L)
                .build();

        //when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PG사 승인 거부");

        //then
        Inventory inventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        assertThat(inventory.getStockQuantity().getValue()).isEqualTo(100);
    }

    @DisplayName("PG 승인 실패 시 주문 상태가 PENDING으로 변경된다")
    @Test
    void rollbackOrderStatusWhenPgApprovalFails(){
        //given
        TestData testData = createTestData("ORD_PG_FAIL_002", 100, 2);

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, "PG사 승인 거부"));

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .pgProvider(PgProvider.TOSS)
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        //when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class);

        //then
        Order order = orderRepository.findById(testData.orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getIsSettleable()).isFalse();
    }

    @DisplayName("PG 승인 실패 시 결제 상태가 FAILED로 변경된다")
    @Test
    void changePaymentStatusToFailedWhenPgApprovalFails(){
        //given
        TestData testData = createTestData("ORD_PG_FAIL_003", 100, 2);

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, "PG사 승인 거부"));

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .paymentKey("test_key")
                .pgProvider(PgProvider.TOSS)
                .amount(20000L)
                .build();

        //when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PG사 승인 거부");

        //then
        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).hasSize(1);

        Payment payment = payments.get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @DisplayName("PG 승인 실패 시 FAILED 로그가 생성된다")
    @Test
    void createFailedLogWhenPgApprovalFails(){
        //given

        TestData testData = createTestData("ORD_PG_FAIL_004", 100, 2);

        String errorMessage = "PG사 승인 거부";
        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, errorMessage));

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .pgProvider(PgProvider.TOSS)
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        // when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class);

        // then
        List<Payment> payments = paymentRepository.findAll();
        Payment payment = payments.get(0);

        List<PaymentLog> paymentLogs = paymentLogRepository.findAllByPaymentId(payment.getId());
        assertThat(paymentLogs)
                .extracting("eventStatus")
                .contains(PaymentEventType.CREATED, PaymentEventType.FAILED);
    }

    @DisplayName("PG 타임아웃 시 재고가 복원된다")
    @Test
    void restoreStockWhenPgTimeout() {
        // given
        TestData testData = createTestData("ORD_PG_TIMEOUT_001", 100, 2);

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_TIMEOUT, "결제 처리 시간 초과"));

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .pgProvider(PgProvider.TOSS)
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        // when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("결제 처리 시간 초과");

        // then - 재고 복원 확인
        Inventory inventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        assertThat(inventory.getStockQuantity().getValue()).isEqualTo(100);
    }

    @DisplayName("PG 승인 성공 후의 실패 시 REQUIRES_MANUAL_CHECK 로그가 생성된다")
    @Test
    void createManualCheckLogWhenTx2FailsAfterPgApproval() {
        // given
        TestData testData = createTestData("ORD_TX2_FAIL_001", 100, 2);

        PaymentResponseDto pgResponse = PaymentResponseDto.builder()
                .paymentKey(null)
                .orderNo(testData.orderNo)
                .method("카드")
                .totalAmount(20000L)
                .pgProvider(PgProvider.TOSS.name())
                .approvedAt(LocalDateTime.now())
                .build();

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenReturn(pgResponse);

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .pgProvider(PgProvider.TOSS)
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        // when
        assertThatThrownBy(() -> paymentService.confirmPayment(request, testData.userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("결제는 승인되었으나 후속 처리 중 오류가 발생했습니다. 고객센터로 문의해주세요.");

        // then
        List<Payment> payments = paymentRepository.findAll();
        Payment payment = payments.get(0);

        List<PaymentLog> logs = paymentLogRepository.findAllByPaymentId(payment.getId());
        assertThat(logs)
                .extracting("eventStatus")
                .contains(PaymentEventType.REQUIRES_MANUAL_CHECK);
    }

    @DisplayName("결제 승인 성공 시 결제가 정상적으로 완료된다")
    @Test
    void confirmPaymentAndCompleteOrder_Success() {
        // given
        TestData testData = createTestData("ORD_SUCCESS_001", 100, 2);

        PaymentResponseDto pgResponse = PaymentResponseDto.builder()
                .paymentKey("test_pg_transaction_123")
                .orderNo(testData.orderNo)
                .method("카드")
                .totalAmount(20000L)
                .pgProvider(PgProvider.TOSS.name())
                .approvedAt(LocalDateTime.now())
                .build();

        when(paymentProcessor.processPayment(any(PaymentConfirmRequest.class)))
                .thenReturn(pgResponse);

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderNo(testData.orderNo)
                .pgProvider(PgProvider.TOSS)
                .paymentKey("test_key")
                .amount(20000L)
                .build();

        // when
        PaymentConfirmResponse response = paymentService.confirmPayment(request, testData.userId);

        // then
        assertThat(response.getOrderNo()).isEqualTo(testData.orderNo);
    }

    @DisplayName("결제 취소 성공 시 결제 상태가 CANCELLED로 변경된다")
    @Test
    void cancelPayment_Success() {
        // given
        TestData testData = createTestData("ORD_CANCEL_001", 100, 2);

        // 결제 생성 및 승인
        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        LocalDateTime cancelledAt = LocalDateTime.of(2025, 11,12, 0,0);

        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        Payment savedPayment = paymentRepository.save(payment);

        // PG 취소 응답 모킹
        PaymentCancelResponseDto pgCancelResponse = PaymentCancelResponseDto.builder()
                .paymentKey("test_pg_tx_123")
                .cancelledAt(cancelledAt)
                .build();

        when(paymentProcessor.processCancelPayment(any(PaymentCancelRequest.class)))
                .thenReturn(pgCancelResponse);

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );


        // when
        PaymentCancelResponse response = paymentService.cancelPayment(request, testData.userId, cancelledAt);

        // then
        assertThat(response.getPaymentKey()).isEqualTo("test_pg_tx_123");
        assertThat(response.getCancelledAt()).isEqualTo(cancelledAt);

        Payment cancelledPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(cancelledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @DisplayName("결제 취소 성공 시 CANCELLED 로그가 생성된다")
    @Test
    void createCancelledLogWhenCancelSuccess() {
        // given
        TestData testData = createTestData("ORD_CANCEL_002", 100, 2);

        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        Payment savedPayment = paymentRepository.save(payment);

        PaymentCancelResponseDto pgCancelResponse = PaymentCancelResponseDto.builder()
                .paymentKey("test_pg_tx_123")
                .cancelledAt(LocalDateTime.now())
                .build();

        when(paymentProcessor.processCancelPayment(any(PaymentCancelRequest.class)))
                .thenReturn(pgCancelResponse);

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );

        // when
        paymentService.cancelPayment(request, testData.userId, LocalDateTime.now());

        // then
        List<PaymentLog> logs = paymentLogRepository.findAllByPaymentId(savedPayment.getId());
        assertThat(logs)
                .extracting("eventStatus")
                .contains(PaymentEventType.CREATED, PaymentEventType.APPROVED, PaymentEventType.CANCELLED);
    }

    @DisplayName("결제 취소 성공 시 재고가 복원된다")
    @Test
    void restoreStockWhenCancelSuccess() {
        // given
        TestData testData = createTestData("ORD_CANCEL_003", 100, 2);

        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        paymentRepository.save(payment);

        // 재고 차감 시뮬레이션
        Inventory inventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        inventory.decrease(2);
        inventoryRepository.save(inventory);

        PaymentCancelResponseDto pgCancelResponse = PaymentCancelResponseDto.builder()
                .paymentKey("test_pg_tx_123")
                .cancelledAt(LocalDateTime.now())
                .build();

        when(paymentProcessor.processCancelPayment(any(PaymentCancelRequest.class)))
                .thenReturn(pgCancelResponse);

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );

        // when
        paymentService.cancelPayment(request, testData.userId, LocalDateTime.now());

        // then
        Inventory restoredInventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        assertThat(restoredInventory.getStockQuantity().getValue()).isEqualTo(100);
    }

    @DisplayName("이미 취소된 결제를 다시 취소하려고 하면 예외가 발생한다")
    @Test
    void cancelAlreadyCancelledPayment() {
        // given
        TestData testData = createTestData("ORD_CANCEL_006", 100, 2);

        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        payment.cancel("이미 취소됨", testData.userId, LocalDateTime.now());
        paymentRepository.save(payment);

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );

        // when & then
        assertThatThrownBy(() -> paymentService.cancelPayment(request, testData.userId, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("결제 취소 상태에서는 취소할 수 없습니다");
    }

    @DisplayName("PG 취소 실패 시 결제 상태가 변경되지 않는다")
    @Test
    void paymentStatusNotChangedWhenPgCancelFails() {
        // given
        TestData testData = createTestData("ORD_CANCEL_008", 100, 2);

        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        Payment savedPayment = paymentRepository.save(payment);

        when(paymentProcessor.processCancelPayment(any(PaymentCancelRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "PG사 취소 거부"));

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );

        // when & then
        assertThatThrownBy(() -> paymentService.cancelPayment(request, testData.userId, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PG사 취소 거부");

        Payment unchangedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @DisplayName("결제 취소 실패 시 재고가 차감되지 않는다")
    @Test
    void stockNotChangedWhenPgCancelFails() {
        // given
        TestData testData = createTestData("ORD_CANCEL_003", 100, 2);

        Payment payment = Payment.create(
                testData.orderId,
                new BigDecimal(20000L),
                PgProvider.TOSS,
                testData.userId
        );
        payment.approve("test_pg_tx_123", testData.userId, LocalDateTime.now(), "카드");
        paymentRepository.save(payment);

        // 재고 차감 시뮬레이션
        Inventory inventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        inventory.decrease(2);
        inventoryRepository.save(inventory);

        PaymentCancelResponseDto pgCancelResponse = PaymentCancelResponseDto.builder()
                .paymentKey("test_pg_tx_123")
                .cancelledAt(LocalDateTime.now())
                .build();

        when(paymentProcessor.processCancelPayment(any(PaymentCancelRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "PG사 취소 거부"));

        PaymentCancelRequest request = new PaymentCancelRequest(
                "고객 변심",
                "test_pg_tx_123",
                PgProvider.TOSS
        );

        // when
        assertThatThrownBy(() -> paymentService.cancelPayment(request, testData.userId, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PG사 취소 거부");

        // then
        Inventory restoredInventory = inventoryRepository.findById(testData.inventoryId).orElseThrow();
        assertThat(restoredInventory.getStockQuantity().getValue()).isEqualTo(98);
    }


    private User createUser() {
        return User.builder()
                .userName("testUser_" + System.nanoTime())
                .password("password123")
                .userEmail("test_" + System.nanoTime() + "@example.com")
                .contactNumber("010-1234-5678")
                .role(UserRole.USER)
                .currentAddress("서울시 강남구")
                .avatarUrl("https://example.com/avatar.jpg")
                .isActive(true)
                .build();
    }

    private Brand createBrand() {
        return Brand.builder()
                .nameKo("테스트 브랜드")
                .nameEn("Test Brand")
                .status(BrandStatus.ACTIVE)
                .commissionRate(new BigDecimal("10.00"))
                .logoUrl("https://example.com/logo.jpg")
                .build();
    }

    private Product createProduct(Brand brand, boolean isAvailable) {
        return Product.builder()
                .brand(brand)
                .productName("테스트 상품")
                .productInfo("테스트 상품 설명")
                .productGenderType(ProductGenderType.ALL)
                .brandName(brand.getNameKo())
                .categoryPath("상의/티셔츠")
                .isAvailable(isAvailable)
                .build();
    }

    private Inventory createInventory(int stockQuantity) {
        return Inventory.builder()
                .stockQuantity(new StockQuantity(stockQuantity))
                .build();
    }

    private ProductOption createProductOption(Product product, Inventory inventory, Long price) {
        return ProductOption.builder()
                .product(product)
                .productPrice(new Money(price))
                .inventory(inventory)
                .build();
    }

    private Order createOrder(Long userId, String orderNo, List<OrderProduct> orderProducts, Long totalPrice) {
        return Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .status(OrderStatus.PENDING)
                .totalPrice(new Money(totalPrice))
                .orderProducts(orderProducts)
                .build();
    }

    private OrderProduct createOrderProduct(ProductOption productOption, int quantity) {
        return OrderProduct.builder()
                .productOption(productOption)
                .productQuantity(quantity)
                .productPrice(productOption.getProductPrice())
                .build();
    }

    private CartItem createCartItem(User user, ProductOption productOption, int quantity) {
        return CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(quantity)
                .build();
    }

    private TestData createTestData(String orderNo, int initialStock, int orderQuantity) {
        User user = userRepository.save(createUser());
        Brand brand = brandRepository.save(createBrand());
        Product product = productRepository.save(createProduct(brand, true));
        Inventory inventory = createInventory(initialStock);
        ProductOption productOption = productOptionRepository.save(
                createProductOption(product, inventory, 10000L)
        );

        List<OrderProduct> orderProducts = new ArrayList<>();
        OrderProduct orderProduct = createOrderProduct(productOption, orderQuantity);
        orderProducts.add(orderProduct);

        Order order = createOrder(user.getId(), orderNo, orderProducts, 20000L);
        orderProducts.forEach(op -> op.setOrderForTest(order));
        orderRepository.save(order);

        return new TestData(
                user.getId(),
                order.getId(),
                inventory.getInventoryId(),
                orderNo
        );
    }

    private static class TestData {
        final Long userId;
        final Long orderId;
        final Long inventoryId;
        final String orderNo;

        TestData(Long userId, Long orderId, Long inventoryId, String orderNo) {
            this.userId = userId;
            this.orderId = orderId;
            this.inventoryId = inventoryId;
            this.orderNo = orderNo;
        }
    }
}