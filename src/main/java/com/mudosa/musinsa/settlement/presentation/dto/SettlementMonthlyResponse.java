package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.settlement.domain.model.SettlementMonthly;
import com.mudosa.musinsa.settlement.domain.model.SettlementStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 월간 정산 조회 응답 DTO
 */
@Getter
@Builder
public class SettlementMonthlyResponse {

    private Long settlementMonthlyId;
    private Long brandId;
    private String brandName;
    private Integer settlementYear;
    private Integer settlementMonth;
    private LocalDate monthStartDate;
    private LocalDate monthEndDate;
    private String settlementNumber;
    private Money totalSalesAmount;
    private Money totalCommissionAmount;
    private Money totalTaxAmount;
    private Money totalPgFeeAmount;
    private Money finalSettlementAmount;
    private Integer totalOrderCount;
    private SettlementStatus settlementStatus;
    private LocalDateTime aggregatedAt;
    private LocalDateTime completedAt;

    public static SettlementMonthlyResponse from(SettlementMonthly monthly, String brandName) {
        return SettlementMonthlyResponse.builder()
                .settlementMonthlyId(monthly.getId())
                .brandId(monthly.getBrandId())
                .brandName(brandName)
                .settlementYear(monthly.getSettlementYear())
                .settlementMonth(monthly.getSettlementMonth())
                .monthStartDate(monthly.getMonthStartDate())
                .monthEndDate(monthly.getMonthEndDate())
                .settlementNumber(monthly.getSettlementNumber())
                .totalSalesAmount(monthly.getTotalSalesAmount())
                .totalCommissionAmount(monthly.getTotalCommissionAmount())
                .totalTaxAmount(monthly.getTotalTaxAmount())
                .totalPgFeeAmount(monthly.getTotalPgFeeAmount())
                .finalSettlementAmount(monthly.getFinalSettlementAmount())
                .totalOrderCount(monthly.getTotalOrderCount())
                .settlementStatus(monthly.getSettlementStatus())
                .aggregatedAt(monthly.getAggregatedAt())
                .completedAt(monthly.getCompletedAt())
                .build();
    }
}