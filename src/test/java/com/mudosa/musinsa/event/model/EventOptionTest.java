package com.mudosa.musinsa.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventOption 엔티티 테스트")
class EventOptionTest {

    @Test
    @DisplayName("[해피케이스] EventOption 생성 - 정상적으로 EventOption을 생성한다")
    void createEventOption_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        BigDecimal eventPrice = new BigDecimal("50000");
        Integer eventStock = 100;

        // when
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(eventPrice)
                .eventStock(eventStock)
                .build();

        // then
        assertThat(eventOption).isNotNull();
        assertThat(eventOption.getEvent()).isEqualTo(event);
        assertThat(eventOption.getEventPrice()).isEqualTo(eventPrice);
        assertThat(eventOption.getEventStock()).isEqualTo(eventStock);
    }

    @Test
    @DisplayName("[해피케이스] EventOption 생성 - eventStock이 null일 경우 0으로 초기화된다")
    void createEventOption_WithNullStock_InitializedToZero() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();

        // when
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(null)
                .eventStock(null)
                .build();

        // then
        assertThat(eventOption.getEventStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("[해피케이스] 재고 증가 - 재고를 증가시킨다")
    void increaseStock_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(100)
                .build();

        // when
        eventOption.increaseStock(50);

        // then
        assertThat(eventOption.getEventStock()).isEqualTo(150);
    }

    @Test
    @DisplayName("[해피케이스] 재고 감소 - 재고를 감소시킨다")
    void decreaseStock_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(100)
                .build();

        // when
        eventOption.decreaseStock(30);

        // then
        assertThat(eventOption.getEventStock()).isEqualTo(70);
    }

    @Test
    @DisplayName("[예외케이스] 재고 감소 - 재고가 부족하면 예외가 발생한다")
    void decreaseStock_WhenInsufficientStock_ThrowsException() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(10)
                .build();

        // when & then
        assertThatThrownBy(() -> eventOption.decreaseStock(20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이벤트 재고가 부족합니다.");
    }

    @Test
    @DisplayName("[예외케이스] 재고 감소 - 재고를 0 미만으로 감소시키면 예외가 발생한다")
    void decreaseStock_WhenResultingNegativeStock_ThrowsException() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("테스트 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        EventOption eventOption = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(5)
                .build();

        // when & then
        assertThatThrownBy(() -> eventOption.decreaseStock(6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이벤트 재고가 부족합니다.");
    }

    @Test
    @DisplayName("[해피케이스] Event 할당 - EventOption에 Event를 할당한다")
    void assignEvent_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event1 = Event.builder()
                .title("테스트 이벤트1")
                .description("설명1")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        Event event2 = Event.builder()
                .title("테스트 이벤트2")
                .description("설명2")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        EventOption eventOption = EventOption.builder()
                .event(event1)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(100)
                .build();

        // when
        eventOption.assignEvent(event2);

        // then
        assertThat(eventOption.getEvent()).isEqualTo(event2);
    }
}
