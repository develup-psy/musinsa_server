package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.settlement.domain.model.SettlementDaily;
import com.mudosa.musinsa.settlement.domain.model.SettlementStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 정산 조회 응답 DTO
 */
@Getter
@Builder
public class SettlementDailyResponse {

    private Long settlementDailyId;
    private Long brandId;
    private String brandName;
    private LocalDate settlementDate;
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

    public static SettlementDailyResponse from(SettlementDaily daily, String brandName) {
        return SettlementDailyResponse.builder()
                .settlementDailyId(daily.getId())
                .brandId(daily.getBrandId())
                .brandName(brandName)
                .settlementDate(daily.getSettlementDate())
                .settlementNumber(daily.getSettlementNumber())
                .totalSalesAmount(daily.getTotalSalesAmount())
                .totalCommissionAmount(daily.getTotalCommissionAmount())
                .totalTaxAmount(daily.getTotalTaxAmount())
                .totalPgFeeAmount(daily.getTotalPgFeeAmount())
                .finalSettlementAmount(daily.getFinalSettlementAmount())
                .totalOrderCount(daily.getTotalOrderCount())
                .settlementStatus(daily.getSettlementStatus())
                .aggregatedAt(daily.getAggregatedAt())
                .completedAt(daily.getCompletedAt())
                .build();
    }
}