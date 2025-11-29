package com.mudosa.musinsa.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventStatus 테스트")
class EventStatusTest {

    @Test
    @DisplayName("[해피케이스] 이벤트 상태 계산 - 시작 전이면 PLANNED를 반환한다")
    void calculateStatus_BeforeStart_ReturnsPlanned() {
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
        LocalDateTime currentTime = LocalDateTime.of(2025, 11, 19, 23, 59);

        // when
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // then
        assertThat(status).isEqualTo(EventStatus.PLANNED);
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 상태 계산 - 진행 중이면 OPEN을 반환한다")
    void calculateStatus_DuringEvent_ReturnsOpen() {
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
        LocalDateTime currentTime = LocalDateTime.of(2025, 11, 25, 12, 0);

        // when
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // then
        assertThat(status).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 상태 계산 - 시작 시간과 동일하면 OPEN을 반환한다")
    void calculateStatus_AtStartTime_ReturnsOpen() {
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
        LocalDateTime currentTime = startedAt;

        // when
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // then
        assertThat(status).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 상태 계산 - 종료 후면 ENDED를 반환한다")
    void calculateStatus_AfterEnd_ReturnsEnded() {
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
        LocalDateTime currentTime = LocalDateTime.of(2025, 12, 21, 0, 0);

        // when
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // then
        assertThat(status).isEqualTo(EventStatus.ENDED);
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 상태 계산 - 종료 시간 직후면 ENDED를 반환한다")
    void calculateStatus_JustAfterEndTime_ReturnsEnded() {
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
        LocalDateTime currentTime = endedAt.plusMinutes(1);

        // when
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // then
        assertThat(status).isEqualTo(EventStatus.ENDED);
    }
}
