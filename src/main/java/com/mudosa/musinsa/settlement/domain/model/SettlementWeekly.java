package com.mudosa.musinsa.settlement.domain.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.common.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "settlements_weekly",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"settlement_number"})
    },
    indexes = {
        @Index(name = "idx_brand_year_month_week", columnList = "brand_id, settlement_year, settlement_month, week_of_month"),
        @Index(name = "idx_weekly_settlement_status", columnList = "settlement_status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class SettlementWeekly extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_settlement_id")
    private Long id;

    @Column(name = "settlement_number", nullable = false, unique = true, length = 50)
    private String settlementNumber;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "settlement_year", nullable = false)
    private Integer settlementYear;

    @Column(name = "settlement_month", nullable = false)
    private Integer settlementMonth;

    @Column(name = "week_of_month", nullable = false)
    private Integer weekOfMonth;

    @Column(name = "settlement_timezone", nullable = false, length = 50)
    private String settlementTimezone;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "week_day_count", nullable = false)
    private Integer weekDayCount;

    @Column(name = "total_order_count", nullable = false)
    private Integer totalOrderCount = 0;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_sales_amount", nullable = false, precision = 15, scale = 2))
    private Money totalSalesAmount = Money.ZERO;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_commission_amount", nullable = false, precision = 15, scale = 2))
    private Money totalCommissionAmount = Money.ZERO;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_tax_amount", nullable = false, precision = 15, scale = 2))
    private Money totalTaxAmount = Money.ZERO;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_pg_fee_amount", nullable = false, precision = 15, scale = 2))
    private Money totalPgFeeAmount = Money.ZERO;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "final_settlement_amount", nullable = false, precision = 15, scale = 2))
    private Money finalSettlementAmount = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    @Column(name = "aggregated_at")
    private LocalDateTime aggregatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 주간 정산 생성
     */
    public static SettlementWeekly create(
        Long brandId,
        int year,
        int weekOfMonth,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        String settlementNumber,
        String timezone
    ) {
        SettlementWeekly settlement = new SettlementWeekly();
        settlement.brandId = brandId;
        settlement.settlementNumber = settlementNumber;
        settlement.weekStartDate = weekStartDate;
        settlement.weekEndDate = weekEndDate;

        // 연도, 월, 주차 설정
        settlement.settlementYear = year;
        settlement.settlementMonth = weekStartDate.getMonthValue();
        settlement.weekOfMonth = weekOfMonth;

        // 실제 일수 계산
        settlement.weekDayCount = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStartDate, weekEndDate) + 1;

        // 타임존 검증
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            settlement.settlementTimezone = timezone;
        } catch (DateTimeException e) {
            log.warn("유효하지 않은 타임존: {}. UTC로 기본 설정합니다.", timezone, e);
            settlement.settlementTimezone = "UTC";
        }

        return settlement;
    }

    /* 해당 월의 몇 번째 주인지 계산 (1-6) */
    private static int calculateWeekOfMonth(LocalDate date) {
        LocalDate firstDayOfMonth = date.withDayOfMonth(1);
        int dayOfMonth = date.getDayOfMonth();
        int firstDayOfWeekValue = firstDayOfMonth.getDayOfWeek().getValue(); // 1(월)~7(일)

        // 첫 주는 월초부터 첫 일요일까지
        return ((dayOfMonth + firstDayOfWeekValue - 2) / 7) + 1;
    }

    /* 일일 정산 추가 (집계) */
    public void addDailySettlement(SettlementDaily daily) {
        this.totalOrderCount += daily.getTotalOrderCount();
        this.totalSalesAmount = this.totalSalesAmount.add(daily.getTotalSalesAmount());
        this.totalCommissionAmount = this.totalCommissionAmount.add(daily.getTotalCommissionAmount());
        this.totalTaxAmount = this.totalTaxAmount.add(daily.getTotalTaxAmount());
        this.totalPgFeeAmount = this.totalPgFeeAmount.add(daily.getTotalPgFeeAmount());
        this.finalSettlementAmount = calculateFinalAmount();
    }

    /* 집계된 데이터 직접 설정 (쿼리 기반 집계용) */
    public void setAggregatedData(
        int totalOrderCount,
        Money totalSalesAmount,
        Money totalCommissionAmount,
        Money totalTaxAmount,
        Money totalPgFeeAmount
    ) {
        this.totalOrderCount = totalOrderCount;
        this.totalSalesAmount = totalSalesAmount;
        this.totalCommissionAmount = totalCommissionAmount;
        this.totalTaxAmount = totalTaxAmount;
        this.totalPgFeeAmount = totalPgFeeAmount;
        this.finalSettlementAmount = calculateFinalAmount();
    }

    /* 최종 정산 금액 계산 */
    private Money calculateFinalAmount() {
        return totalSalesAmount
            .subtract(totalCommissionAmount)
            .subtract(totalTaxAmount)
            .subtract(totalPgFeeAmount);
    }

    /* 집계 처리 시작 */
    public void startProcessing() {
        this.settlementStatus = SettlementStatus.PROCESSING;
        this.aggregatedAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    /* 정산 완료 */
    public void complete() {
        this.settlementStatus = SettlementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    /* 정산 실패 */
    public void fail() {
        this.settlementStatus = SettlementStatus.FAILED;
    }

    /* 일평균 매출 계산 (짧은 주 보정) */
    public Money getAverageDailySales() {
        if (weekDayCount == null || weekDayCount == 0) {
            return Money.ZERO;
        }
        return totalSalesAmount.divide(java.math.BigDecimal.valueOf(weekDayCount));
    }
}
