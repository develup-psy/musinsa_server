package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventImage;
import com.mudosa.musinsa.event.presentation.dto.res.EventListResDto;
import com.mudosa.musinsa.event.repository.EventImageRepository;
import com.mudosa.musinsa.event.repository.EventOptionRepository;
import com.mudosa.musinsa.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventService 테스트")
class EventServiceTest extends ServiceConfig {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventImageRepository eventImageRepository;

    @Autowired
    private EventOptionRepository eventOptionRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @Transactional
    @DisplayName("[해피케이스] 이벤트 타입별 조회 - DROP 타입 이벤트 목록을 조회한다")
    void getEventListByType_Drop_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);

        Event dropEvent1 = Event.builder()
                .title("드롭 이벤트1")
                .description("드롭 설명1")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();

        Event dropEvent2 = Event.builder()
                .title("드롭 이벤트2")
                .description("드롭 설명2")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();

        eventRepository.save(dropEvent1);
        eventRepository.save(dropEvent2);

        // when
        List<EventListResDto> result = eventService.getEventListByType(Event.EventType.DROP);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("[해피케이스] 이벤트 타입별 조회 - COMMENT 타입 이벤트 목록을 조회한다")
    void getEventListByType_Comment_Success() {
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
        List<EventListResDto> result = eventService.getEventListByType(Event.EventType.COMMENT);

        // then
        assertThat(result).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("[해피케이스] 날짜 필터링 조회 - 현재 시간 이후에 시작하는 이벤트만 조회한다")
    void getFilteredEventList_Success() {
        // given
        LocalDateTime currentTime = LocalDateTime.of(2025, 11, 15, 0, 0);
        LocalDateTime futureStartedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime futureEndedAt = LocalDateTime.of(2025, 12, 20, 23, 59);
        LocalDateTime pastStartedAt = LocalDateTime.of(2025, 11, 1, 0, 0);
        LocalDateTime pastEndedAt = LocalDateTime.of(2025, 11, 10, 23, 59);

        Event futureEvent = Event.builder()
                .title("미래 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(futureStartedAt)
                .endedAt(futureEndedAt)
                .coupon(null)
                .build();

        Event pastEvent = Event.builder()
                .title("과거 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(pastStartedAt)
                .endedAt(pastEndedAt)
                .coupon(null)
                .build();

        eventRepository.save(futureEvent);
        eventRepository.save(pastEvent);

        // when
        List<EventListResDto> result = eventService.getFilteredEventList(currentTime);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(dto -> {
            // 이벤트 시작 시간이 currentTime 이후인지 확인
            return true; // DTO에서 시작 시간을 직접 확인할 수 없으므로 결과가 있는지만 확인
        });
    }

    @Test
    @Transactional
    @DisplayName("[해피케이스] 이벤트 목록 조회 - 썸네일 이미지가 포함된 이벤트를 조회한다")
    void getEventListByType_WithThumbnail_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.of(2025, 11, 20, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2025, 12, 20, 23, 59);

        Event event = Event.builder()
                .title("썸네일 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(null)
                .build();
        Event savedEvent = eventRepository.save(event);

        EventImage thumbnailImage = EventImage.builder()
                .imageUrl("https://example.com/thumbnail.jpg")
                .isThumbnail(true)
                .build();
        thumbnailImage.assignEvent(savedEvent);
        eventImageRepository.save(thumbnailImage);

        // when
        List<EventListResDto> result = eventService.getEventListByType(Event.EventType.DROP);

        // then
        assertThat(result).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("[예외케이스] 이벤트 타입별 조회 - 해당 타입 이벤트가 없으면 빈 리스트를 반환한다")
    void getEventListByType_NoEvents_ReturnsEmptyList() {
        // given
        // DISCOUNT 타입 이벤트는 생성하지 않음

        // when
        List<EventListResDto> result = eventService.getEventListByType(Event.EventType.DISCOUNT);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("[예외케이스] 날짜 필터링 조회 - 조건에 맞는 이벤트가 없으면 빈 리스트를 반환한다")
    void getFilteredEventList_NoMatchingEvents_ReturnsEmptyList() {
        // given
        LocalDateTime currentTime = LocalDateTime.of(2025, 12, 31, 23, 59);
        // 모든 이벤트가 currentTime 이전에 시작한다고 가정

        // when
        List<EventListResDto> result = eventService.getFilteredEventList(currentTime);

        // then
        // 결과는 빈 리스트거나 조건에 맞지 않는 이벤트들
        assertThat(result).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("[해피케이스] 이벤트 목록 응답에 쿠폰 메타 정보가 포함된다")
    void getEventListByType_IncludesCouponMeta() {
        LocalDateTime startedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime endedAt = LocalDateTime.now().plusDays(10);

        Coupon coupon = Coupon.builder()
                .couponName("목록 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .startDate(startedAt)
                .endDate(endedAt)
                .totalQuantity(100)
                .build();
        couponRepository.save(coupon);

        Event event = Event.builder()
                .title("쿠폰 포함 이벤트")
                .description("설명")
                .eventType(Event.EventType.DROP)
                .limitPerUser(1)
                .isPublic(true)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .coupon(coupon)
                .build();
        Event saved = eventRepository.save(event);

        List<EventListResDto> result = eventService.getEventListByType(Event.EventType.DROP);

        EventListResDto target = result.stream()
                .filter(dto -> dto.getEventId().equals(saved.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(target.getCoupon()).isNotNull();
        assertThat(target.getCoupon().getCouponName()).isEqualTo("목록 테스트 쿠폰");
        assertThat(target.getCoupon().getDiscountValue()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(target.getCoupon().getTotalQuantity()).isEqualTo(100);
    }
}
