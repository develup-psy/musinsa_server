package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.payment.application.dto.*;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.PaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentConfirmResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentQueueResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentStatusResponse;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentEventType;
import com.mudosa.musinsa.payment.domain.model.PaymentStatus;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.mudosa.musinsa.exception.ErrorCode.PAYMENT_APPROVAL_FAILED;
import static com.mudosa.musinsa.exception.ErrorCode.PAYMENT_TIMEOUT;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProcessor paymentProcessor;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentQueueService paymentQueueService;
    private final PaymentRepository paymentRepository;

    @Observed(name = "payment.confirmAndCompleteOrder", contextualName = "결제승인")
    public PaymentConfirmResponse confirmPayment(PaymentConfirmRequest request, Long userId) {
        Long paymentId = null;
        Long orderId = null;
        boolean pgApproved = false;

        try {
            // TX1: 결제 생성
            PaymentCreationResult creationResult = paymentConfirmService.createPayment(
                    request.toPaymentCreateRequest(), userId
            );

            paymentId = creationResult.getPaymentId();
            orderId = creationResult.getOrderId();

            // 트랜잭션 아님: PG 승인 요청
            PaymentResponseDto pgResponse = paymentProcessor.processPayment(request);
            pgApproved = true;

            // TX2: 결제 승인
            paymentConfirmService.approvePayment(paymentId, userId, pgResponse, orderId);

            return PaymentConfirmResponse.builder()
                    .orderNo(request.getOrderNo())
                    .build();

        } catch (BusinessException e) {
            // 결제 생성 전 오류 → 롤백이 되기 때문에 보상할게 없음
            if (paymentId == null) {
                if (e.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
                    throw e;
                }
                throw new BusinessException(ErrorCode.PAYMENT_FAILED_BEFORE_PG_CONFIRM, e.getMessage());
            }

            // PG사에 의한 오류 처리
            if (!pgApproved && isPgRelatedError(e.getErrorCode())) {
                paymentConfirmService.failPayment(paymentId, e.getMessage(), userId, orderId);
                throw e;
            }

            if (pgApproved) {
                paymentConfirmService.manualPaymentCheck(paymentId, userId);
                throw new BusinessException(
                        ErrorCode.PAYMENT_SYSTEM_ERROR,
                        "결제는 승인되었으나 후속 처리 중 오류가 발생했습니다. 고객센터로 문의해주세요."
                );
            }

            throw e;
        }
    }

    private boolean isPgRelatedError(ErrorCode errorCode) {
        return errorCode == PAYMENT_APPROVAL_FAILED
                || errorCode == PAYMENT_TIMEOUT;
    }

    @Observed(name = "payment.confirmPaymentAsync", contextualName = "결제승인-대기열")
    public PaymentQueueResponse confirmPaymentAsync(PaymentConfirmRequest request, Long userId) {
        Long paymentId = null;
        Long orderId = null;

        try {
            // TX1: 주문 완료(재고 차감) + 결제 생성 (QUEUED 상태)
            PaymentCreationResult creationResult = paymentConfirmService.createQueuedPayment(
                    request.toPaymentCreateRequest(),
                    request.getPaymentKey(),
                    userId
            );

            paymentId = creationResult.getPaymentId();
            orderId = creationResult.getOrderId();

            // Redis 대기열 등록
            EnqueueResult enqueueResult = paymentQueueService.enqueue(paymentId);

            // 대기열 가득 참 → 즉시 실패 처리
            if (enqueueResult.isRejected()) {
                paymentConfirmService.failPayment(paymentId, "대기열 초과", userId, orderId);
                throw new BusinessException(ErrorCode.PAYMENT_QUEUE_FULL);
            }

            return PaymentQueueResponse.builder()
                    .ticketId(paymentId)
                    .queuePosition(enqueueResult.getRank() + 1)
                    .estimatedWaitSeconds(enqueueResult.estimateWaitSeconds(100))
                    .status("QUEUED")
                    .orderNo(request.getOrderNo())
                    .build();

        } catch (BusinessException e) {
            // 재고 부족 등 TX1 이전 오류
            if (paymentId == null) {
                throw e;
            }
            // TX1 이후 대기열 등록 실패 → 보상 트랜잭션
            if (e.getErrorCode() != ErrorCode.PAYMENT_QUEUE_FULL) {
                paymentConfirmService.failPayment(paymentId, e.getMessage(), userId, orderId);
            }
            throw e;
        }
    }

    /**
     * 결제 상태 조회 (대기열 기반 결제의 현재 처리 상태)
     * - QUEUED: 대기 중 → ZRANK로 순번 반환
     * - APPROVED: 결제 완료
     * - FAILED: 실패 (최종 실패 로그에서 사유 추출)
     * - PENDING: 처리 중
     */
    @Observed(name = "payment.getStatus", contextualName = "결제-상태조회")
    public PaymentStatusResponse getPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        return switch (payment.getStatus()) {
            case QUEUED -> buildQueuedResponse(paymentId);
            case APPROVED -> PaymentStatusResponse.approved(payment);
            case FAILED -> PaymentStatusResponse.failed(extractFailReason(payment));
            default -> PaymentStatusResponse.pending();
        };
    }

    private PaymentStatusResponse buildQueuedResponse(Long paymentId) {
        Long position = paymentQueueService.getPosition(paymentId);
        long safePosition = (position != null) ? position : 0;
        long waitSeconds = paymentQueueService.estimateWaitSeconds(safePosition);
        return PaymentStatusResponse.queued(safePosition, waitSeconds);
    }

    private String extractFailReason(Payment payment) {
        return payment.getPaymentLogs().stream()
                .filter(log -> log.getEventStatus() == PaymentEventType.FAILED)
                .reduce((first, second) -> second)
                .map(log -> log.getEventMessage())
                .orElse("알 수 없는 오류");
    }

    @Observed(name = "payment.cancelRequest", contextualName = "결제-취소-요청")
    public PaymentCancelResponse cancelPayment(PaymentCancelRequest request, Long userId, LocalDateTime cancelledAt) {
        try {
            // TX1: 결제 상태 변경, 주문 관련 원복
            paymentConfirmService.cancelPayment(
                    request.getPaymentTransactionId(),
                    request.getCancelReason(),
                    userId,
                    cancelledAt
            );

            // 트랜잭션 아님: 외부 PG사 호출
            PaymentCancelResponseDto pgResponse =
                    paymentProcessor.processCancelPayment(request);

            return new PaymentCancelResponse(pgResponse);
        } catch (BusinessException e) {
            paymentConfirmService.failCancel(request.getPaymentTransactionId(), e.getMessage(), userId);
            throw e;
        }
    }
}
