package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.OrderService;
import com.mudosa.musinsa.payment.application.dto.PaymentCreateDto;
import com.mudosa.musinsa.payment.application.dto.PaymentCreationResult;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentEventType;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final PaymentCompensationService paymentCompensationService;
    private final PaymentQueueOutboxService paymentQueueOutboxService;

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
                request.getOrderNo(),
                request.getTotalAmount(),
                request.getPgProvider(),
                userId
        );

        paymentRepository.save(payment);

        return PaymentCreationResult.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .createdAt(payment.getCreatedAt())
                .build();
    }

    @Observed(name = "payment.transaction.createQueued", contextualName = "결제-트랜잭션-대기열생성")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentCreationResult createQueuedPayment(PaymentCreateDto request, String paymentKey, Long userId) {
        Long orderId = null;
        try {
            // 주문 완료(재고 차감, 주문 상태 변경)
            orderId = orderService.completeOrder(request.getOrderNo());

            // 결제 생성 (QUEUED 상태)
            Payment payment = Payment.createQueued(
                    orderId,
                    request.getOrderNo(),
                    request.getTotalAmount(),
                    request.getPgProvider(),
                    paymentKey,
                    userId
            );

            paymentRepository.saveAndFlush(payment);
            paymentQueueOutboxService.createPendingEvent(payment.getId(), request.getOrderNo());

            return PaymentCreationResult.builder()
                    .paymentId(payment.getId())
                    .orderId(orderId)
                    .userId(userId)
                    .createdAt(payment.getCreatedAt())
                    .build();
        } catch (DataIntegrityViolationException e) {
            compensateQueuedCreationFailure(orderId, e);
            if (isDuplicatePaymentKeyException(e)) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_CREATE_FAILED,
                        "이미 처리된 paymentKey입니다. 새로운 결제 요청으로 다시 시도해주세요."
                );
            }
            throw new BusinessException(ErrorCode.PAYMENT_CREATE_FAILED, "결제 생성 중 데이터 무결성 오류가 발생했습니다.");
        } catch (RuntimeException e) {
            compensateQueuedCreationFailure(orderId, e);
            throw e;
        }
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

    @Observed(name = "payment.startProcessing", contextualName = "결제-처리시작")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startProcessingIfQueued(Long paymentId, Long userId) {
        int updatedRows = paymentRepository.updateStatusIfCurrent(
                paymentId,
                PaymentStatus.QUEUED,
                PaymentStatus.PENDING,
                LocalDateTime.now()
        );
        return updatedRows == 1;
    }

    @Observed(name = "payment.requeue", contextualName = "결제-재대기")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean requeueIfPending(Long paymentId, String reason, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);

        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }

        payment.requeue(reason, userId);
        paymentRepository.save(payment);
        return true;
    }


    @Observed(name = "payment.manualCheck", contextualName = "결제-수동확인")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void manualPaymentCheck(Long paymentId, Long userId){
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return;
        }
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

    private void compensateQueuedCreationFailure(Long orderId, Exception cause) {
        if (orderId == null) {
            return;
        }

        try {
            paymentCompensationService.rollbackOrderAfterQueuedCreationFailure(orderId);
        } catch (Exception compensationException) {
            log.error("[PaymentConfirmService] queued 결제 생성 실패 보상 실패: orderId={}", orderId, compensationException);
            throw new BusinessException(
                    ErrorCode.PAYMENT_SYSTEM_ERROR,
                    "결제 요청 복구 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            );
        }

        log.warn("[PaymentConfirmService] queued 결제 생성 실패 보상 실행: orderId={}, cause={}",
                orderId, cause.getClass().getSimpleName());
    }

    private boolean isDuplicatePaymentKeyException(DataIntegrityViolationException e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null) {
            message = e.getMessage();
        }
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("duplicate")
                || lower.contains("duplicate entry")
                || lower.contains("unique constraint");
    }
}
