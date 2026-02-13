package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.OrderService;
import com.mudosa.musinsa.order.domain.model.Order;
import com.mudosa.musinsa.payment.application.dto.PaymentCreateDto;
import com.mudosa.musinsa.payment.application.dto.PaymentCreationResult;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentEventType;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    @Observed(name = "payment.transaction.create", contextualName = "결제-트랜잭션-생성")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected PaymentCreationResult createPayment(PaymentCreateDto request, Long userId) {
        // 주문 완료(재고 차감, 주문 상태 변경)
        Long orderId =
                orderService.completeOrder(
                request.getOrderNo()
        );

        // 결제 생성
        Payment payment = Payment.create(
                orderId,
                request.getTotalAmount(),
                request.getPgProvider(),
                userId
        );

        paymentRepository.save(payment);

        return PaymentCreationResult.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .build();
    }

    /**
     * 대기열 기반 결제 생성 (status = QUEUED)
     * TX1: 주문 완료(재고 차감) + 결제 생성(QUEUED 상태)
     */
    @Observed(name = "payment.transaction.createQueued", contextualName = "결제-트랜잭션-대기열생성")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentCreationResult createQueuedPayment(PaymentCreateDto request, String paymentKey, Long userId) {
        // 주문 완료(재고 차감, 주문 상태 변경)
        Long orderId = orderService.completeOrder(request.getOrderNo());

        // 결제 생성 (QUEUED 상태)
        Payment payment = Payment.createQueued(
                orderId,
                request.getTotalAmount(),
                request.getPgProvider(),
                paymentKey,
                userId
        );

        paymentRepository.save(payment);

        return PaymentCreationResult.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .build();
    }

    @Observed(name = "payment.approve", contextualName = "결제-승인")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void approvePayment(Long paymentId, Long userId, PaymentResponseDto paymentResponseDto, Long orderId) {
        //장바구니 삭제
        orderService.deleteCartItems(orderId, userId);

        //결제 조회
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        String pgTransactionId = paymentResponseDto.getPaymentKey();

        //결제 상태 변경
        payment.approve(pgTransactionId, userId, paymentResponseDto.getApprovedAt(), paymentResponseDto.getMethod());

        paymentRepository.save(payment);
    }

    @Observed(name = "payment.fail", contextualName = "결제-실패")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failPayment(Long paymentId, String errorMessage, Long userId, Long orderId) {
        //주문 및 재고 롤백
        orderService.rollbackOrder(orderId);

        //결제 상태 변경
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.fail(errorMessage, userId);

        paymentRepository.save(payment);
    }


    @Observed(name = "payment.manualCheck", contextualName = "결제-수동확인")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void manualPaymentCheck(Long paymentId, Long userId){
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        payment.addLog(PaymentEventType.REQUIRES_MANUAL_CHECK,
                "PG 승인 후 예상치 못한 오류 발생", userId);
        paymentRepository.save(payment);
    }

    @Observed(name = "payment.cancel", contextualName = "결제-취소")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelPayment(String paymentTransactionId, String cancelReason, Long userId, LocalDateTime cancelledAt) {
        log.info(paymentTransactionId);

        //결제 조회
        Payment payment = paymentRepository.findByPgTransactionId(paymentTransactionId);

        //결제 상태 변경
        payment.cancel(cancelReason, userId, cancelledAt);
        paymentRepository.save(payment);

        //주문 관련 원복
        orderService.cancelOrder(payment.getOrderId());
    }

    @Observed(name = "payment.cancelFail", contextualName = "결제-취소실패")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failCancel(String paymentKey, String message, Long userId) {
        //결제 상태 변경
        Payment payment = paymentRepository.findByPgTransactionId(paymentKey);
        payment.cancelFail(message, userId);

        //주문 및 재고 롤백
        orderService.rollbackOrderCancel(payment.getOrderId());
    }
}
