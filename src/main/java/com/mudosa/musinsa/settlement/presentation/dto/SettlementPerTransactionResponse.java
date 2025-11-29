package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.settlement.domain.model.SettlementPerTransaction;
import com.mudosa.musinsa.settlement.domain.model.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 거래별 정산 응답 DTO
 */
@Getter
@Builder
public class SettlementPerTransactionResponse {

    private Long settlementTransactionId;
    private Long brandId;
    private String brandName;
    private Long paymentId;
    private String pgTransactionId;
    private LocalDateTime transactionDate;
    private LocalDate transactionDateLocal;
    private TransactionType transactionType;
    private Money transactionAmount;
    private BigDecimal commissionRate;
    private Money commissionAmount;
    private Money taxAmount;
    private Money pgFeeAmount;
    private Money finalSettlementAmount;
    private String timezoneOffset;
    private LocalDateTime createdAt;

    public static SettlementPerTransactionResponse from(
        SettlementPerTransaction transaction,
        String brandName
    ) {
        return SettlementPerTransactionResponse.builder()
                .settlementTransactionId(transaction.getId())
                .brandId(transaction.getBrandId())
                .brandName(brandName)
                .paymentId(transaction.getPaymentId())
                .pgTransactionId(transaction.getPgTransactionId())
                .transactionDate(transaction.getTransactionDate())
                .transactionDateLocal(transaction.getTransactionDateLocal())
                .transactionType(transaction.getTransactionType())
                .transactionAmount(transaction.getTransactionAmount())
                .commissionRate(transaction.getCommissionRate())
                .commissionAmount(transaction.getCommissionAmount())
                .taxAmount(transaction.getTaxAmount())
                .pgFeeAmount(transaction.getPgFeeAmount())
                .finalSettlementAmount(transaction.calculateFinalSettlementAmount())
                .timezoneOffset(transaction.getTimezoneOffset())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
