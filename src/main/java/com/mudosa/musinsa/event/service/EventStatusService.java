package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventStatus;
import com.mudosa.musinsa.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled; //스케줄러 사용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 상태 변경 로직 (시스템 단) 분리
 *
 * 목적:
 * - 주기적으로 이벤트의 상태를 자동으로 업데이트
 * - PLANNED → OPEN: 시작 시간이 도래한 이벤트
 * - OPEN → ENDED: 종료 시간이 지난 이벤트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final EventRepository eventRepository;

    /**
     * 이벤트 상태 자동 업데이트 (1분마다 실행)
     *
     * cron 표현식: "초 분 시 일 월 요일"
     * "0 * * * * *" = 매분 0초에 실행
     *
     * 모든 이벤트의 started_at, ended_at을 현재 시간과 비교하여
     * DRAFT/PLANNED → OPEN → ENDED 방향으로만 상태를 업데이트합니다.
     * (되돌림 없음: 이미 진행된 이벤트는 되돌리지 않음)
     */
    @Scheduled(cron = "0 * * * * *")  // 매분 실행
    @Transactional
    public void updateEventStatuses() {
        LocalDateTime now = LocalDateTime.now();

        log.info("이벤트 상태 자동 동기화 시작 - {}", now);

        // CANCELLED, PAUSED 제외한 모든 이벤트 조회 (수동 관리 상태 제외)
        List<Event> events = eventRepository.findAll().stream()
                .filter(event -> event.getStatus() != EventStatus.CANCELLED
                        && event.getStatus() != EventStatus.PAUSED)
                .toList();

        int updatedCount = 0;
        int toOpenCount = 0;
        int toEndedCount = 0;

        for (Event event : events) {
            EventStatus currentStatus = event.getStatus();
            EventStatus calculatedStatus = EventStatus.calculateStatus(event, now);

            // 단방향 진행만 허용: DRAFT/PLANNED → OPEN → ENDED
            // 되돌림은 하지 않음 (예: OPEN → PLANNED, ENDED → OPEN 불가)
            if (currentStatus != calculatedStatus) {
                switch (calculatedStatus) {
                    case PLANNED -> {
                        // PLANNED로 되돌리지 않음 (이미 OPEN이나 ENDED면 유지)
                        log.debug("이벤트 상태 유지 (되돌리지 않음): {} - eventId: {}, title: {}",
                                currentStatus, event.getId(), event.getTitle());
                    }
                    case OPEN -> {
                        // DRAFT 또는 PLANNED → OPEN
                        // 공개 이벤트만 자동 OPEN
                        if ((currentStatus == EventStatus.DRAFT || currentStatus == EventStatus.PLANNED)
                            && event.getIsPublic()) {
                            event.open();
                            toOpenCount++;
                            updatedCount++;
                            log.info("이벤트 상태 변경: {} → OPEN - eventId: {}, title: {}, startedAt: {}, endedAt: {}",
                                    currentStatus, event.getId(), event.getTitle(), event.getIsPublic(), event.getStartedAt(), event.getEndedAt());
                        } else if(!event.getIsPublic()){
                            log.debug("비공개 이벤트는 자동 OPEN하지 않음 - eventId: {}, title: {}, isPublic: false ",
                                    event.getId(), event.getTitle());
                        }
                    }
                    case ENDED -> {
                        // OPEN → ENDED (DRAFT/PLANNED에서 바로 ENDED로는 가지 않음)
                        if (currentStatus == EventStatus.OPEN) {
                            event.end();
                            toEndedCount++;
                            updatedCount++;
                            log.info("이벤트 상태 변경: {} → ENDED - eventId: {}, title: {}, endedAt: {}",
                                    currentStatus, event.getId(), event.getTitle(), event.getEndedAt());
                        } else if (currentStatus == EventStatus.DRAFT || currentStatus == EventStatus.PLANNED) {
                            // DRAFT/PLANNED 상태에서 종료일이 지난 경우 ENDED로 직접 전환
                            event.end();
                            toEndedCount++;
                            updatedCount++;
                            log.info("이벤트 상태 변경: {} → ENDED (시작하지 않고 종료) - eventId: {}, title: {}",
                                    currentStatus, event.getId(), event.getTitle());
                        }
                    }
                }
            }
        }

        if (updatedCount > 0) {
            log.info("이벤트 상태 자동 동기화 완료 - OPEN: {}건, ENDED: {}건, 전체: {}건 / 검사: {}건",
                    toOpenCount, toEndedCount, updatedCount, events.size());
        } else {
            log.debug("이벤트 상태 변경 없음 - 검사: {}건", events.size());
        }
    }

    /**
     * 수동으로 특정 이벤트의 상태를 업데이트 (즉시 실행)
     *
     * @param eventId 업데이트할 이벤트 ID
     */

    @Transactional
    public void updateEventStatus(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + eventId));

        LocalDateTime now = LocalDateTime.now();
        EventStatus currentStatus = event.getStatus();
        EventStatus calculatedStatus = EventStatus.calculateStatus(event, now);

        // 계산된 상태와 현재 DB 상태가 다르면 업데이트
        if (currentStatus != calculatedStatus) {
            switch (calculatedStatus) {
                case OPEN -> event.open();
                case ENDED -> event.end();
                // PLANNED는 자동으로 변경하지 않음 (시작 전 상태는 수동 관리)
            }
            log.info("이벤트 상태 수동 업데이트 - eventId: {}, {} → {}",
                    eventId, event.getStatus(), calculatedStatus);
        }
    }

    /**
     * 모든 이벤트의 상태를 강제로 동기화 (관리자용, 데이터 정합성 복구 시 사용)
     */
    @Transactional
    public int syncAllEventStatuses() {
        log.info("전체 이벤트 상태 동기화 시작");

        List<Event> allEvents = eventRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        int updatedCount = 0;

        for (Event event : allEvents) {
            EventStatus currentStatus = event.getStatus();
            EventStatus calculatedStatus = EventStatus.calculateStatus(event, now);

            // CANCELLED는 건드리지 않음
            if (currentStatus == EventStatus.CANCELLED) {
                continue;
            }

            // 계산된 상태와 다르면 업데이트
            if (currentStatus != calculatedStatus) {
                switch (calculatedStatus) {
                    case PLANNED -> {
                        // PLANNED로 되돌리지 않음 (이미 OPEN이면 유지)
                    }
                    case OPEN -> {
                        if (currentStatus != EventStatus.PAUSED && event.getIsPublic()) {  // PAUSED는 수동 관리
                            event.open();
                            updatedCount++;
                        }
                    }
                    case ENDED -> {
                        event.end();
                        updatedCount++;
                    }
                }
            }
        }

        log.info("전체 이벤트 상태 동기화 완료 - 업데이트: {}건 / 전체: {}건", updatedCount, allEvents.size());
        return updatedCount;
    }
}
