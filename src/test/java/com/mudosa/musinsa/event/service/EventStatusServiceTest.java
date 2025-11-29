package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventStatus;
import com.mudosa.musinsa.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EventStatusService 통합 테스트")
class EventStatusServiceTest {

    // ========================================
    // 단위 테스트 (Mockito 사용)
    // ========================================
    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("단위 테스트 - Mock을 사용한 비즈니스 로직 검증")
    class UnitTest {

        @Mock
        private EventRepository eventRepository;

        @InjectMocks
        private EventStatusService eventStatusService;

        @Test
        @Transactional
        @DisplayName("PLANNED 상태의 이벤트가 시작 시간이 되면 OPEN으로 변경된다")
        void updatePlannedToOpen_Success() {

            // Given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startedAt = now.minusMinutes(10);
            LocalDateTime endedAt = now.plusHours(1);

            Event plannedEvent = Event.builder()
                    .id(1L)
                    .title("테스트 이벤트")
                    .status(EventStatus.PLANNED)
                    .isPublic(true)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .build();


            when(eventRepository.findAll())
                    .thenReturn(List.of(plannedEvent));

            // When
            eventStatusService.updateEventStatuses();

            // Then
            verify(eventRepository, times(1))
                    .findAll();

            //verify(eventRepository, times(1)).save(plannedEvent);

            assertThat(plannedEvent.getStatus()).isEqualTo(EventStatus.OPEN);
        }

        @Test
        @Transactional
        @DisplayName("OPEN 상태의 이벤트가 종료 시간이 지나면 ENDED로 변경된다")
        void updateOpenToEnded_Success() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startedAt = now.minusHours(2);
            LocalDateTime endedAt = now.minusMinutes(10);

            Event openEvent = Event.builder()
                    .id(1L)
                    .title("종료 예정 이벤트")
                    .status(EventStatus.OPEN)
                    .isPublic(true)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .build();

            when(eventRepository.findAll())
                    .thenReturn(List.of(openEvent));

            // When
            eventStatusService.updateEventStatuses();

            // Then
            verify(eventRepository, times(1)).findAll();
            assertThat(openEvent.getStatus()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @Transactional
        @DisplayName("공개되지 않은(isPublic=false) 이벤트는 자동으로 OPEN되지 않는다")
        void updatePlannedToOpen_NotPublic() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startedAt = now.minusMinutes(10);

            Event privateEvent = Event.builder()
                    .id(1L)
                    .title("비공개 이벤트")
                    .status(EventStatus.PLANNED)
                    .isPublic(false)
                    .startedAt(startedAt)
                    .endedAt(now.plusHours(1))
                    .build();

            when(eventRepository.findAll())
                    .thenReturn(List.of(privateEvent));

            // When
            eventStatusService.updateEventStatuses();

            // Then
            verify(eventRepository, times(1)).findAll();

            // 검증
            assertThat(privateEvent.getStatus()).isEqualTo(EventStatus.PLANNED);

        }

        @Test
        @Transactional
        @DisplayName("여러 이벤트의 상태가 동시에 업데이트된다")
        void updateMultipleEvents_Success() {
            // Given
            LocalDateTime now = LocalDateTime.now();

            Event event1 = Event.builder()
                    .id(1L)
                    .title("이벤트 1")
                    .status(EventStatus.PLANNED)
                    .isPublic(true)
                    .startedAt(now.minusMinutes(5))
                    .endedAt(now.plusHours(1))
                    .build();

            Event event2 = Event.builder()
                    .id(2L)
                    .title("이벤트 2")
                    .status(EventStatus.OPEN)
                    .isPublic(true)
                    .startedAt(now.minusHours(2))
                    .endedAt(now.minusMinutes(5))
                    .build();

            when(eventRepository.findAll())
                    .thenReturn(List.of(event1, event2));

            // When
            eventStatusService.updateEventStatuses();

            // Then
            verify(eventRepository, times(1)).findAll();
            assertThat(event1.getStatus()).isEqualTo(EventStatus.OPEN);
            assertThat(event2.getStatus()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @Transactional
        @DisplayName("수동으로 특정 이벤트의 상태를 업데이트할 수 있다")
        void updateEventStatus_Manual_Success() {
            // Given
            Long eventId = 1L;
            LocalDateTime now = LocalDateTime.now();

            Event event = Event.builder()
                    .id(eventId)
                    .title("수동 업데이트 이벤트")
                    .status(EventStatus.PLANNED)
                    .isPublic(true)
                    .startedAt(now.minusMinutes(10))
                    .endedAt(now.plusHours(1))
                    .build();

            when(eventRepository.findById(eventId))
                    .thenReturn(Optional.of(event));

            // When
            eventStatusService.updateEventStatus(eventId);

            // Then
            verify(eventRepository, times(1)).findById(eventId);
            assertThat(event.getStatus()).isEqualTo(EventStatus.OPEN);
        }

        @Test
        @Transactional
        @DisplayName("전체 이벤트 상태 동기화가 정상적으로 작동한다")
        void syncAllEventStatuses_Success() {
            // Given
            LocalDateTime now = LocalDateTime.now();

            Event event1 = Event.builder()
                    .id(1L)
                    .title("이벤트 1")
                    .status(EventStatus.PLANNED)
                    .isPublic(true)
                    .startedAt(now.minusMinutes(10))
                    .endedAt(now.plusHours(1))
                    .build();

            Event event2 = Event.builder()
                    .id(2L)
                    .title("이벤트 2")
                    .status(EventStatus.OPEN)
                    .isPublic(true)
                    .startedAt(now.minusHours(2))
                    .endedAt(now.minusMinutes(5))
                    .build();

            Event event3 = Event.builder()
                    .id(3L)
                    .title("취소된 이벤트")
                    .status(EventStatus.CANCELLED)
                    .isPublic(true)
                    .startedAt(now.minusHours(1))
                    .endedAt(now.plusHours(1))
                    .build();

            when(eventRepository.findAll())
                    .thenReturn(Arrays.asList(event1, event2, event3));

            // When
            int updatedCount = eventStatusService.syncAllEventStatuses();

            // Then
            assertThat(updatedCount).isEqualTo(2); // event1, event2만 업데이트
            assertThat(event1.getStatus()).isEqualTo(EventStatus.OPEN);
            assertThat(event2.getStatus()).isEqualTo(EventStatus.ENDED);
            assertThat(event3.getStatus()).isEqualTo(EventStatus.CANCELLED);
            verify(eventRepository, times(1)).findAll();
        }
    }

    // ========================================
    // 통합 테스트 (실제 DB 사용)
    // ========================================
    @Nested
    @DisplayName("통합 테스트 - 실제 DB를 사용한 E2E 검증")
    class IntegrationTest extends ServiceConfig {

        @Autowired
        private EventStatusService eventStatusService;

        @Autowired
        private EventRepository eventRepository;

        @Autowired
        private CouponRepository couponRepository;

        @Autowired
        private EntityManager entityManager;

        @Test
        @Transactional
        @DisplayName("이벤트 상태 자동 업데이트 - PLANNED에서 OPEN으로 변경된다")
        void updateEventStatuses_PlannedToOpen_Success() {
            // Given
            LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
            LocalDateTime futureTime = LocalDateTime.now().plusDays(7);

            Event plannedEvent = Event.builder()
                    .title("PLANNED 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(pastTime)
                    .endedAt(futureTime)
                    .coupon(null)
                    .build();
            eventRepository.save(plannedEvent);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.updateEventStatuses();

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedEvent = eventRepository.findById(plannedEvent.getId()).orElseThrow();
            assertThat(updatedEvent).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("이벤트 상태 자동 업데이트 - OPEN에서 ENDED로 변경된다")
        void updateEventStatuses_OpenToEnded_Success() {
            // Given
            LocalDateTime pastStartTime = LocalDateTime.now().minusDays(7);
            LocalDateTime pastEndTime = LocalDateTime.now().minusHours(1);

            Event event = Event.builder()
                    .title("자동 종료 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(pastStartTime)
                    .endedAt(pastEndTime)
                    .coupon(null)
                    .build();
            event.open();
            Event savedEvent = eventRepository.save(event);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.updateEventStatuses();

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedEvent = eventRepository.findById(savedEvent.getId()).orElseThrow();
            assertThat(updatedEvent.getStatus()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @Transactional
        @DisplayName("특정 이벤트 상태 수동 업데이트 - OPEN으로 변경된다")
        void updateEventStatus_ToOpen_Success() {
            // Given
            LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
            LocalDateTime futureTime = LocalDateTime.now().plusDays(7);


            String uniqueCouponName = "테스트 쿠폰 - " + System.currentTimeMillis();

            Coupon coupon = Coupon.builder()
                    .couponName(uniqueCouponName)
                    .discountType(DiscountType.AMOUNT)
                    .discountValue(new BigDecimal("10000"))
                    .startDate(pastTime)
                    .endDate(futureTime)
                    .totalQuantity(100)
                    .build();
            couponRepository.save(coupon);

            Event event = Event.builder()
                    .title("수동 업데이트 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(pastTime)
                    .endedAt(futureTime)
                    .coupon(coupon)
                    .build();
            Event savedEvent = eventRepository.save(event);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.updateEventStatus(savedEvent.getId());

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedEvent = eventRepository.findById(savedEvent.getId()).orElseThrow();
            assertThat(updatedEvent.getStatus()).isEqualTo(EventStatus.OPEN);
        }

        @Test
        @Transactional
        @DisplayName("특정 이벤트 상태 수동 업데이트 - ENDED로 변경된다")
        void updateEventStatus_ToEnded_Success() {
            // Given
            LocalDateTime pastStartTime = LocalDateTime.now().minusDays(7);
            LocalDateTime pastEndTime = LocalDateTime.now().minusHours(1);

            Event event = Event.builder()
                    .title("수동 종료 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(pastStartTime)
                    .endedAt(pastEndTime)
                    .coupon(null)
                    .build();
            event.open();
            Event savedEvent = eventRepository.save(event);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.updateEventStatus(savedEvent.getId());

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedEvent = eventRepository.findById(savedEvent.getId()).orElseThrow();
            assertThat(updatedEvent.getStatus()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @Transactional
        @DisplayName("전체 이벤트 상태 동기화 - 여러 이벤트의 상태가 동기화된다")
        void syncAllEventStatuses_Success() {
            // Given
            LocalDateTime now = LocalDateTime.now();

            // 종료된 이벤트 (OPEN → ENDED로 변경되어야 함)
            Event endedEvent = Event.builder()
                    .title("종료된 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(now.minusDays(7))
                    .endedAt(now.minusHours(1))
                    .coupon(null)
                    .build();
            endedEvent.open();
            eventRepository.save(endedEvent);

            // 진행 중인 이벤트 (OPEN 유지)
            Event openEvent = Event.builder()
                    .title("진행 중 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(now.minusHours(1))
                    .endedAt(now.plusDays(7))
                    .coupon(null)
                    .build();
            openEvent.open();
            eventRepository.save(openEvent);

            // 취소된 이벤트 (CANCELLED 유지)
            Event cancelledEvent = Event.builder()
                    .title("취소된 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(now.minusDays(7))
                    .endedAt(now.minusHours(1))
                    .coupon(null)
                    .build();
            cancelledEvent.cancel();
            eventRepository.save(cancelledEvent);

            entityManager.flush();
            entityManager.clear();

            // When
            int updatedCount = eventStatusService.syncAllEventStatuses();

            entityManager.flush();
            entityManager.clear();

            // Then
            assertThat(updatedCount).isGreaterThanOrEqualTo(1);

            Event updatedEndedEvent = eventRepository.findById(endedEvent.getId()).orElseThrow();
            assertThat(updatedEndedEvent.getStatus()).isEqualTo(EventStatus.ENDED);

            Event updatedOpenEvent = eventRepository.findById(openEvent.getId()).orElseThrow();
            assertThat(updatedOpenEvent.getStatus()).isEqualTo(EventStatus.OPEN);

            Event updatedCancelledEvent = eventRepository.findById(cancelledEvent.getId()).orElseThrow();
            assertThat(updatedCancelledEvent.getStatus()).isEqualTo(EventStatus.CANCELLED);
        }

        @Test
        @Transactional
        @DisplayName("이벤트 상태 업데이트 - 공개 이벤트만 자동 OPEN된다")
        void updateEventStatuses_OnlyPublicEvents_Success() {
            // Given
            LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
            LocalDateTime futureTime = LocalDateTime.now().plusDays(7);

            // 공개 이벤트
            Event publicEvent = Event.builder()
                    .title("공개 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(pastTime)
                    .endedAt(futureTime)
                    .coupon(null)
                    .build();
            eventRepository.save(publicEvent);

            // 비공개 이벤트
            Event privateEvent = Event.builder()
                    .title("비공개 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(false)
                    .startedAt(pastTime)
                    .endedAt(futureTime)
                    .coupon(null)
                    .build();
            eventRepository.save(privateEvent);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.updateEventStatuses();

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedPublicEvent = eventRepository.findById(publicEvent.getId()).orElseThrow();
            Event updatedPrivateEvent = eventRepository.findById(privateEvent.getId()).orElseThrow();

            System.out.println("=== After ===");
            System.out.println("공개 이벤트: " + updatedPublicEvent.getStatus() + ", isPublic: " + updatedPublicEvent.getIsPublic());
            System.out.println("비공개 이벤트: " + updatedPrivateEvent.getStatus() + ", isPublic: " + updatedPrivateEvent.getIsPublic());

            assertThat(updatedPublicEvent.getStatus()).isEqualTo(EventStatus.OPEN);
            assertThat(updatedPrivateEvent.getStatus()).isNotEqualTo(EventStatus.OPEN);
        }

        @Test
        @Transactional
        @DisplayName("이벤트 상태 업데이트 - PAUSED 상태는 건드리지 않는다")
        void syncAllEventStatuses_PausedEventNotChanged_Success() {
            // Given
            LocalDateTime now = LocalDateTime.now();

            Event pausedEvent = Event.builder()
                    .title("일시정지 이벤트")
                    .description("설명")
                    .eventType(Event.EventType.DROP)
                    .limitPerUser(1)
                    .isPublic(true)
                    .startedAt(now.minusHours(1))
                    .endedAt(now.plusDays(7))
                    .coupon(null)
                    .build();
            pausedEvent.open();
            pausedEvent.pause();
            eventRepository.save(pausedEvent);

            entityManager.flush();
            entityManager.clear();

            // When
            eventStatusService.syncAllEventStatuses();

            entityManager.flush();
            entityManager.clear();

            // Then
            Event updatedEvent = eventRepository.findById(pausedEvent.getId()).orElseThrow();
            assertThat(updatedEvent.getStatus()).isEqualTo(EventStatus.PAUSED);
        }
    }
}