package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.order.application.OrderService;
import com.mudosa.musinsa.payment.application.dto.*;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.PaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentConfirmResponse;
import com.mudosa.musinsa.payment.domain.model.Payment;
import com.mudosa.musinsa.payment.domain.model.PaymentEventType;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import com.mudosa.musinsa.payment.domain.repository.PaymentRepository;
import com.mudosa.musinsa.payment.application.event.PaymentApprovedEvent;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.mudosa.musinsa.exception.ErrorCode.PAYMENT_APPROVAL_FAILED;
import static com.mudosa.musinsa.exception.ErrorCode.PAYMENT_TIMEOUT;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProcessor paymentProcessor;
    private final PaymentConfirmService paymentConfirmService;

    @Observed(name = "payment.confirmAndCompleteOrder", contextualName = "결제승인-주문완료")
    public PaymentConfirmResponse confirmPaymentAndCompleteOrder(PaymentConfirmRequest request, Long userId) {
        Long paymentId = null;
        Long orderId = null;
        boolean pgApproved = false;

        try{
            //TX1: 결제 생성
            PaymentCreationResult creationResult = paymentConfirmService.createPaymentTransaction(request.toPaymentCreateRequest(), userId);

            paymentId = creationResult.getPaymentId();
            orderId = creationResult.getOrderId();

            //트랜잭션 아님: PG 승인 요청
            PaymentResponseDto pgResponse = paymentProcessor.processPayment(request);
            pgApproved = true;

            //TX2: 결제 승인
            paymentConfirmService.approvePayment(paymentId, userId, pgResponse, orderId);

            return PaymentConfirmResponse.builder()
                    .orderNo(request.getOrderNo())
                    .build();

        }catch(BusinessException e){
            //결제 생성 전 오류 -> 롤백이 되기 때문에 보상할게 없음
            if(paymentId == null){
                if(e.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK){
                    throw e;
                }

                throw new BusinessException(ErrorCode.PAYMENT_FAILED_BEFORE_PG_CONFIRM, e.getMessage());
            }

            //PG사에 의한 오류 처리
            if(!pgApproved && isPgRelatedError(e.getErrorCode())){
                paymentConfirmService.failPayment(paymentId, e.getMessage(), userId, orderId);
                throw e;
            }

            if(pgApproved){
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

    @Observed(name = "payment.cancelRequest", contextualName = "결제-취소-요청")
    public PaymentCancelResponse cancelPayment(PaymentCancelRequest request, Long userId, LocalDateTime cancelledAt) {
        try{
            //TX1: 결제 상태 변경, 주문 관련 원복
            paymentConfirmService.cancelPayment(request.getPaymentTransactionId(), request.getCancelReason(), userId, cancelledAt);

            //트랜젹션 아님: 외부 PG사 호출
            PaymentCancelResponseDto pgResponse =
                    paymentProcessor.processCancelPayment(request);

            return new PaymentCancelResponse(pgResponse);
        }catch (BusinessException e){
            paymentConfirmService.failCancel(request.getPaymentTransactionId(), e.getMessage(), userId);
            throw e;
        }

    }
}
