package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.settlement.domain.model.SettlementStatus;
import com.mudosa.musinsa.settlement.domain.model.SettlementWeekly;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 정산 조회 응답 DTO
 */
@Getter
@Builder
public class SettlementWeeklyResponse {

    private Long settlementWeeklyId;
    private Long brandId;
    private String brandName;
    private Integer settlementYear;
    private Integer settlementMonth;
    private Integer weekOfMonth;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer weekDayCount;
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

    public static SettlementWeeklyResponse from(SettlementWeekly weekly, String brandName) {
        return SettlementWeeklyResponse.builder()
                .settlementWeeklyId(weekly.getId())
                .brandId(weekly.getBrandId())
                .brandName(brandName)
                .settlementYear(weekly.getSettlementYear())
                .settlementMonth(weekly.getSettlementMonth())
                .weekOfMonth(weekly.getWeekOfMonth())
                .weekStartDate(weekly.getWeekStartDate())
                .weekEndDate(weekly.getWeekEndDate())
                .weekDayCount(weekly.getWeekDayCount())
                .settlementNumber(weekly.getSettlementNumber())
                .totalSalesAmount(weekly.getTotalSalesAmount())
                .totalCommissionAmount(weekly.getTotalCommissionAmount())
                .totalTaxAmount(weekly.getTotalTaxAmount())
                .totalPgFeeAmount(weekly.getTotalPgFeeAmount())
                .finalSettlementAmount(weekly.getFinalSettlementAmount())
                .totalOrderCount(weekly.getTotalOrderCount())
                .settlementStatus(weekly.getSettlementStatus())
                .aggregatedAt(weekly.getAggregatedAt())
                .completedAt(weekly.getCompletedAt())
                .build();
    }
}