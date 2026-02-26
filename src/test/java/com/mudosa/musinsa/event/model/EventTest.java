package com.mudosa.musinsa.event.model;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Event 엔티티 테스트")
class EventTest {

    @Test
    @DisplayName("[해피케이스] Event 생성 - 정상적으로 Event를 생성한다")
    void createEvent_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        Coupon coupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .startDate(startedAt)
                .endDate(endedAt)
                .totalQuantity(100)
                .build();

        // when
        Event event = Event.builder()
                .title("신상품 드롭 이벤트")
                .description("2025년 겨울 신상품 드롭")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(coupon)
                .build();

        // then
        assertThat(event).isNotNull();
        assertThat(event.getTitle()).isEqualTo("신상품 드롭 이벤트");
        assertThat(event.getDescription()).isEqualTo("2025년 겨울 신상품 드롭");
        assertThat(event.getEventType()).isEqualTo(Event.EventType.DROP);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getIsPublic()).isTrue();
        assertThat(event.getLimitPerUser()).isEqualTo(1);
        assertThat(event.getStartedAt()).isEqualTo(startedAt);
        assertThat(event.getEndedAt()).isEqualTo(endedAt);
        assertThat(event.getCoupon()).isEqualTo(coupon);
    }

    @Test
    @DisplayName("[해피케이스] Event 상태 변경 - OPEN 상태로 변경한다")
    void changeEventStatus_ToOpen_Success() {
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
        event.open();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("[해피케이스] Event 상태 변경 - PAUSED 상태로 변경한다")
    void changeEventStatus_ToPaused_Success() {
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
        event.open();

        // when
        event.pause();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.PAUSED);
    }

    @Test
    @DisplayName("[해피케이스] Event 상태 변경 - ENDED 상태로 변경한다")
    void changeEventStatus_ToEnded_Success() {
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
        event.end();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);
    }

    @Test
    @DisplayName("[해피케이스] Event 상태 변경 - CANCELLED 상태로 변경한다")
    void changeEventStatus_ToCancelled_Success() {
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
        event.cancel();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    @DisplayName("[해피케이스] 진행중인 이벤트 확인 - 현재 시간이 이벤트 기간 내이고 상태가 OPEN이면 true를 반환한다")
    void isOngoing_WhenCurrentTimeInPeriodAndStatusOpen_ReturnsTrue() {
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
        event.open();
        LocalDateTime now = LocalDateTime.of(2025, 11, 25, 12, 0);

        // when
        boolean isOngoing = event.isOngoing(now);

        // then
        assertThat(isOngoing).isTrue();
    }

    @Test
    @DisplayName("[예외케이스] 진행중인 이벤트 확인 - 현재 시간이 이벤트 시작 전이면 false를 반환한다")
    void isOngoing_WhenCurrentTimeBeforeStart_ReturnsFalse() {
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
        event.open();
        LocalDateTime now = LocalDateTime.of(2025, 11, 19, 12, 0);

        // when
        boolean isOngoing = event.isOngoing(now);

        // then
        assertThat(isOngoing).isFalse();
    }

    @Test
    @DisplayName("[예외케이스] 진행중인 이벤트 확인 - 현재 시간이 이벤트 종료 후면 false를 반환한다")
    void isOngoing_WhenCurrentTimeAfterEnd_ReturnsFalse() {
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
        event.open();
        LocalDateTime now = LocalDateTime.of(2025, 12, 21, 0, 0);

        // when
        boolean isOngoing = event.isOngoing(now);

        // then
        assertThat(isOngoing).isFalse();
    }

    @Test
    @DisplayName("[예외케이스] 진행중인 이벤트 확인 - 상태가 OPEN이 아니면 false를 반환한다")
    void isOngoing_WhenStatusNotOpen_ReturnsFalse() {
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
        // status는 DRAFT 상태
        LocalDateTime now = LocalDateTime.of(2025, 11, 25, 12, 0);

        // when
        boolean isOngoing = event.isOngoing(now);

        // then
        assertThat(isOngoing).isFalse();
    }

    @Test
    @DisplayName("[해피케이스] EventOption 추가 - 이벤트에 옵션을 추가한다")
    void addEventOption_Success() {
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
        EventOption option = EventOption.builder()
                .event(event)
                .productOption(null)
                .eventPrice(new BigDecimal("50000"))
                .eventStock(100)
                .build();

        // when
        event.addEventOption(option);

        // then
        assertThat(event.getEventOptions()).hasSize(1);
        assertThat(event.getEventOptions().get(0)).isEqualTo(option);
        assertThat(option.getEvent()).isEqualTo(event);
    }

    @Test
    @DisplayName("[해피케이스] EventImage 추가 - 이벤트에 이미지를 추가한다")
    void addEventImage_Success() {
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
        EventImage image = EventImage.builder()
                .imageUrl("https://example.com/image.jpg")
                .isThumbnail(true)
                .build();

        // when
        event.addEventImage(image);

        // then
        assertThat(event.getEventImages()).hasSize(1);
        assertThat(event.getEventImages().get(0)).isEqualTo(image);
        assertThat(image.getEvent()).isEqualTo(event);
    }

    @Test
    @DisplayName("[해피케이스] Coupon 할당 - 이벤트에 쿠폰을 할당한다")
    void assignCoupon_Success() {
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
        Coupon coupon = Coupon.builder()
                .couponName("새 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startedAt)
                .endDate(endedAt)
                .totalQuantity(100)
                .build();

        // when
        event.assignCoupon(coupon);

        // then
        assertThat(event.getCoupon()).isEqualTo(coupon);
    }
}
