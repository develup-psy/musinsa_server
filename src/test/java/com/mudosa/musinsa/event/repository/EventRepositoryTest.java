package com.mudosa.musinsa.event.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventRepository 테스트")
class EventRepositoryTest extends ServiceConfig {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("[해피케이스] 이벤트 저장 - 이벤트를 정상적으로 저장한다")
    void saveEvent_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Event event = Event.builder()
                .title("신상품 드롭 이벤트")
                .description("2025년 겨울 신상품 드롭")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();

        // when
        Event savedEvent = eventRepository.save(event);

        // then
        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getTitle()).isEqualTo("신상품 드롭 이벤트");
        assertThat(savedEvent.getDescription()).isEqualTo("2025년 겨울 신상품 드롭");
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 조회 - ID로 이벤트를 조회한다")
    void findEventById_Success() {
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
        Event savedEvent = eventRepository.save(event);

        // when
        Optional<Event> foundEvent = eventRepository.findById(savedEvent.getId());

        // then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getId()).isEqualTo(savedEvent.getId());
        assertThat(foundEvent.get().getTitle()).isEqualTo("테스트 이벤트");
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 타입별 조회 - DROP 타입 이벤트를 조회한다")
    void findAllByEventType_Drop_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);

        Event dropEvent = Event.builder()
                .title("드롭 이벤트")
                .description("드롭 설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        Event commentEvent = Event.builder()
                .title("댓글 이벤트")
                .description("댓글 설명")
                .eventType(Event.EventType.COMMENT)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        eventRepository.save(dropEvent);
        eventRepository.save(commentEvent);

        // when
        List<Event> dropEvents = eventRepository.findAllByEventType(Event.EventType.DROP);

        // then
        assertThat(dropEvents).isNotEmpty();
        assertThat(dropEvents).allMatch(e -> e.getEventType() == Event.EventType.DROP);
    }

    @Test
    @DisplayName("[해피케이스] 이벤트 타입별 조회 - COMMENT 타입 이벤트를 조회한다")
    void findAllByEventType_Comment_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);

        Event commentEvent = Event.builder()
                .title("댓글 이벤트")
                .description("댓글 설명")
                .eventType(Event.EventType.COMMENT)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        eventRepository.save(commentEvent);

        // when
        List<Event> commentEvents = eventRepository.findAllByEventType(Event.EventType.COMMENT);

        // then
        assertThat(commentEvents).isNotEmpty();
        assertThat(commentEvents).allMatch(e -> e.getEventType() == Event.EventType.COMMENT);
    }

    @Test
    @DisplayName("[해피케이스] 상태와 종료일로 조회 - 종료일이 지난 OPEN 상태 이벤트를 조회한다")
    void findAllByStatusAndEndedAtBefore_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 1, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 11, 10, 23, 59);

        Event event = Event.builder()
                .title("종료 예정 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        event.open();
        eventRepository.save(event);

        LocalDateTime checkTime = LocalDateTime.of(2025, 11, 15, 0, 0);

        // when
        List<Event> endedEvents = eventRepository.findAllByStatusAndEndedAtBefore(EventStatus.OPEN, checkTime);

        // then
        assertThat(endedEvents).isNotEmpty();
        assertThat(endedEvents).allMatch(e ->
            e.getStatus() == EventStatus.OPEN && e.getEndedAt().isBefore(checkTime)
        );
    }

    @Test
    @DisplayName("[해피케이스] 상태와 시작일로 조회 - 시작일이 지난 PLANNED 상태 이벤트를 조회한다")
    void findAllByStatusAndStartedAtBefore_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 5, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);

        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startedAt)
                .endDate(endedAt)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        Event event = Event.builder()
                .title("시작 예정 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(coupon)
                .build();
        // PLANNED 상태를 시뮬레이션하기 위해 DRAFT 상태로 유지
        eventRepository.save(event);

        LocalDateTime checkTime = LocalDateTime.of(2025, 11, 10, 0, 0);

        // when
        List<Event> plannedEvents = eventRepository.findAllByStatusAndStartedAtBefore(EventStatus.DRAFT, checkTime);

        // then
        assertThat(plannedEvents).isNotEmpty();
        assertThat(plannedEvents).allMatch(e ->
            e.getStatus() == EventStatus.DRAFT && e.getStartedAt().isBefore(checkTime)
        );
    }

    @Test
    @DisplayName("[예외케이스] 존재하지 않는 ID 조회 - 빈 Optional을 반환한다")
    void findEventById_NotFound_ReturnsEmpty() {
        // given
        Long nonExistentId = 999999L;

        // when
        Optional<Event> foundEvent = eventRepository.findById(nonExistentId);

        // then
        assertThat(foundEvent).isEmpty();
    }

    @Test
    @DisplayName("[예외케이스] 이벤트 타입별 조회 - 해당 타입 이벤트가 없으면 빈 리스트를 반환한다")
    void findAllByEventType_NoEvents_ReturnsEmptyList() {
        // given
        // DISCOUNT 타입 이벤트는 생성하지 않음

        // when
        List<Event> discountEvents = eventRepository.findAllByEventType(Event.EventType.DISCOUNT);

        // then
        assertThat(discountEvents).isEmpty();
    }
}
