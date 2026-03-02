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
    name = "settlements_daily",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"settlement_number"}),
        @UniqueConstraint(columnNames = {"brand_id", "settlement_date"})
    },
    indexes = {
        @Index(name = "idx_settlement_date", columnList = "settlement_date"),
        @Index(name = "idx_daily_brand_id", columnList = "brand_id"),
        @Index(name = "idx_daily_settlement_status", columnList = "settlement_status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class SettlementDaily extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_settlement_id")
    private Long id;

    @Column(name = "settlement_number", nullable = false, unique = true, length = 50)
    private String settlementNumber;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "settlement_timezone", nullable = false, length = 50)
    private String settlementTimezone;

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

    /* 일일 정산 생성 */
    public static SettlementDaily create(
        Long brandId,
        LocalDate settlementDate,
        String settlementNumber,
        String timezone
    ) {
        SettlementDaily settlement = new SettlementDaily();
        settlement.brandId = brandId;
        settlement.settlementDate = settlementDate;
        settlement.settlementNumber = settlementNumber;

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

    /* 거래 추가 (집계) */
    public void addTransaction(SettlementPerTransaction transaction) {
        this.totalOrderCount++;
        this.totalSalesAmount = this.totalSalesAmount.add(transaction.getTransactionAmount());
        this.totalCommissionAmount = this.totalCommissionAmount.add(transaction.getCommissionAmount());
        this.totalTaxAmount = this.totalTaxAmount.add(transaction.getTaxAmount());
        this.totalPgFeeAmount = this.totalPgFeeAmount.add(transaction.getPgFeeAmount());
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
}
