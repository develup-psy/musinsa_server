package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.settlement.domain.model.SettlementStatus;
import com.mudosa.musinsa.settlement.domain.model.SettlementYearly;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 연간 정산 조회 응답 DTO
 */
@Getter
@Builder
public class SettlementYearlyResponse {

    private Long settlementYearlyId;
    private Long brandId;
    private String brandName;
    private Integer settlementYear;
    private LocalDate yearStartDate;
    private LocalDate yearEndDate;
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

    public static SettlementYearlyResponse from(SettlementYearly yearly, String brandName) {
        return SettlementYearlyResponse.builder()
                .settlementYearlyId(yearly.getId())
                .brandId(yearly.getBrandId())
                .brandName(brandName)
                .settlementYear(yearly.getSettlementYear())
                .yearStartDate(yearly.getYearStartDate())
                .yearEndDate(yearly.getYearEndDate())
                .settlementNumber(yearly.getSettlementNumber())
                .totalSalesAmount(yearly.getTotalSalesAmount())
                .totalCommissionAmount(yearly.getTotalCommissionAmount())
                .totalTaxAmount(yearly.getTotalTaxAmount())
                .totalPgFeeAmount(yearly.getTotalPgFeeAmount())
                .finalSettlementAmount(yearly.getFinalSettlementAmount())
                .totalOrderCount(yearly.getTotalOrderCount())
                .settlementStatus(yearly.getSettlementStatus())
                .aggregatedAt(yearly.getAggregatedAt())
                .completedAt(yearly.getCompletedAt())
                .build();
    }
}