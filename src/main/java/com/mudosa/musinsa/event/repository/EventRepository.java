package com.mudosa.musinsa.event.repository;

import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;



/**
 * Event Repository
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {



    List<Event> findAllByEventType(
            Event.EventType eventType
    );

    // N+1 문제 해결: Fetch Join을 사용하여 연관 엔티티를 한 번의 쿼리로 조회
    // eventOptions와 productOption, product를 한 번에 로드
    // eventImages는 @BatchSize로 별도 최적화 (MultipleBagFetchException 방지)
    @Query("""
        SELECT DISTINCT e
        FROM Event e
        LEFT JOIN FETCH e.eventOptions eo
        LEFT JOIN FETCH eo.productOption po
        LEFT JOIN FETCH po.product p
        WHERE e.eventType = :eventType
    """)
    List<Event> findAllByEventTypeWithRelations(@Param("eventType") Event.EventType eventType);

    // 자동 상태 업데이트를 위한 쿼리
    List<Event> findAllByStatusAndEndedAtBefore(EventStatus status, LocalDateTime time);

    List<Event> findAllByStatusAndStartedAtBefore(EventStatus status, LocalDateTime time);

}
