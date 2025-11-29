package com.mudosa.musinsa.event.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "event_option",
        uniqueConstraints = {
                // DDL: UNIQUE KEY `uk_event_product_option` (`event_id`, `product_option_id`)
                @UniqueConstraint(name = "uk_event_product_option", columnNames = {"event_id", "product_option_id"})
        },
        indexes = {
                // DDL: INDEX `idx_evtopt_event` (`event_id`)
                @Index(name = "idx_evtopt_event", columnList = "event_id"),
                // DDL: INDEX `idx_evtopt_popt` (`product_option_id`)
                @Index(name = "idx_evtopt_popt", columnList = "product_option_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_option_id")
    private Long id;

    /** FK: event.event_id (소유측) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** FK: product_option.product_option_id (소유측) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    /**
     * DDL: DECIMAL(10,2) NULL
     * - null이면 "일반 가격 사용" 의미 (비즈니스 규칙)
     */
    @Column(name = "event_price", precision = 10, scale = 2)
    private BigDecimal eventPrice;

    /** DDL: INT NOT NULL DEFAULT 0 */
    @Column(name = "event_stock", nullable = false)
    @Builder.Default
    private Integer eventStock = 0;

    /** Event 편의 메서드에서 호출 (Event.addEventOption) */
    void assignEvent(Event event) {
        this.event = event;
    }

    /** 재고 증감 유틸 (선택) */
    public void increaseStock(int qty) {
        this.eventStock += qty;
    }
    public void decreaseStock(int qty) {
        int next = this.eventStock - qty;
        if (next < 0) {
            throw new IllegalStateException("이벤트 재고가 부족합니다.");
        }
        this.eventStock = next;
    }
}
