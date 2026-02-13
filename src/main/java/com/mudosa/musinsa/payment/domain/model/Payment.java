package com.mudosa.musinsa.payment.domain.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;
    
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name="payment_status")
    private PaymentStatus status;

    private String currency = "KRW";

    private String method;
    
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PgProvider pgProvider;
    
    private String pgTransactionId;

    private Long userId;
    
    private LocalDateTime approvedAt;
    private LocalDateTime cancelledAt;
    
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentLog> paymentLogs = new ArrayList<>();

    @Builder
    private Payment(Long orderId, PaymentStatus status, String currency, String method, BigDecimal amount, PgProvider pgProvider, String pgTransactionId, Long userId, LocalDateTime approvedAt, LocalDateTime cancelledAt, List<PaymentLog> paymentLogs) {
        this.orderId = orderId;
        this.status = status;
        this.currency = currency != null ? currency : "KRW";
        this.method = method;
        this.amount = amount;
        this.pgProvider = pgProvider;
        this.pgTransactionId = pgTransactionId;
        this.userId = userId;
        this.approvedAt = approvedAt;
        this.cancelledAt = cancelledAt;
        this.paymentLogs = (paymentLogs != null ? paymentLogs : new ArrayList<>());
    }

    public static Payment create(
            Long orderId,
            BigDecimal amount,
            PgProvider pgProvider,
            Long userId) {

        //검증
        validateRequiredParameters(orderId, amount, pgProvider, userId);

        // 금액은 0보다 커야 함
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CREATE_FAILED,
                    "결제 금액은 0보다 커야 합니다"
            );
        }

        //결제 생성
        Payment payment = Payment.builder()
                .status(PaymentStatus.PENDING)
                .orderId(orderId)
                .pgProvider(pgProvider)
                .amount(amount)
                .userId(userId)
                .build();

        //결제 로그 추가
        PaymentLog paymentLog = PaymentLog.create(payment, PaymentEventType.CREATED,null, userId);
        payment.paymentLogs.add(paymentLog);

        return payment;
    }

    /**
     * 대기열 기반 결제 생성 (status = QUEUED)
     * PG 호출 전 대기열에 등록될 결제
     */
    public static Payment createQueued(
            Long orderId,
            BigDecimal amount,
            PgProvider pgProvider,
            String paymentKey,
            Long userId) {

        validateRequiredParameters(orderId, amount, pgProvider, userId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CREATE_FAILED,
                    "결제 금액은 0보다 커야 합니다"
            );
        }

        Payment payment = Payment.builder()
                .status(PaymentStatus.QUEUED)
                .orderId(orderId)
                .pgProvider(pgProvider)
                .pgTransactionId(paymentKey)
                .amount(amount)
                .userId(userId)
                .build();

        PaymentLog paymentLog = PaymentLog.create(payment, PaymentEventType.CREATED, "대기열 등록", userId);
        payment.paymentLogs.add(paymentLog);

        return payment;
    }

    public void approve(String pgTransactionId, Long userId, LocalDateTime approvedAt, String method) {

        //검증
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PG_TRANSACTION_ID);
        }

        validateUserId(userId);

        this.status = this.status.approve();
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = approvedAt;
        this.method = method;

        addLog(PaymentEventType.APPROVED, null, userId);
    }

    public void fail(String errorMessage, Long userId) {
        validateUserId(userId);
        this.status = this.status.fail();

        addLog(PaymentEventType.FAILED, errorMessage, userId);
    }

    public void cancel(String errorMessage, Long userId, LocalDateTime cancelledAt) {
        validateUserId(userId);
        this.status = this.status.cancel();
        this.cancelledAt = cancelledAt;

        addLog(PaymentEventType.CANCELLED, errorMessage, userId);
    }

    public void cancelFail(String errorMessage, Long userId) {
        validateUserId(userId);
        this.status = this.status.rollback();

        addLog(PaymentEventType.CANCEL_FAILED, errorMessage, userId);
    }


    public void addLog(PaymentEventType eventType, String message, Long userId) {
        validateUserId(userId);
        PaymentLog log = PaymentLog.create(this, eventType, message, userId);
        this.paymentLogs.add(log);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "사용자 ID는 필수입니다"
            );
        }
    }

    private static void validateRequiredParameters(Long orderId, BigDecimal amount, PgProvider pgProvider, Long userId) {
        if (orderId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "주문 ID는 필수입니다"
            );
        }

        if (amount == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "결제 금액은 필수입니다"
            );
        }

        if (pgProvider == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "PG사는 필수입니다"
            );
        }

        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "사용자 ID는 필수입니다"
            );
        }
    }

}
