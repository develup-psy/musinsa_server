package com.mudosa.musinsa.settlement.presentation.dto;

import com.mudosa.musinsa.common.vo.Money;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 통계 응답 DTO
 *
 * 브랜드별 기간별 정산 통계 제공
 * 오늘/이번주/이번달/올해/전체 기간 매출액 및 주문 건수
 */
@Getter
@Builder
public class SettlementStatisticsResponse {

    private Long brandId;
    private String brandName;

    private Money todaySalesAmount;
    private Integer todayOrderCount;

    private Money thisWeekSalesAmount;
    private Integer thisWeekOrderCount;

    private Money thisMonthSalesAmount;
    private Integer thisMonthOrderCount;

    private Money thisYearSalesAmount;
    private Integer thisYearOrderCount;

    private Money totalSalesAmount;
    private Integer totalOrderCount;

    public static SettlementStatisticsResponse of(
        Long brandId,
        String brandName,
        Money todaySalesAmount,
        Integer todayOrderCount,
        Money thisWeekSalesAmount,
        Integer thisWeekOrderCount,
        Money thisMonthSalesAmount,
        Integer thisMonthOrderCount,
        Money thisYearSalesAmount,
        Integer thisYearOrderCount,
        Money totalSalesAmount,
        Integer totalOrderCount
    ) {
        return SettlementStatisticsResponse.builder()
                .brandId(brandId)
                .brandName(brandName)
                .todaySalesAmount(todaySalesAmount)
                .todayOrderCount(todayOrderCount)
                .thisWeekSalesAmount(thisWeekSalesAmount)
                .thisWeekOrderCount(thisWeekOrderCount)
                .thisMonthSalesAmount(thisMonthSalesAmount)
                .thisMonthOrderCount(thisMonthOrderCount)
                .thisYearSalesAmount(thisYearSalesAmount)
                .thisYearOrderCount(thisYearOrderCount)
                .totalSalesAmount(totalSalesAmount)
                .totalOrderCount(totalOrderCount)
                .build();
    }
}