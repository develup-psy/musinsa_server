package com.mudosa.musinsa.event.presentation.dto.res;


import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder

public class EventListResDto {
    /*
    - 실제 API 응답에 사용할 DTO 클래스
    - controller => client로 데이터를 전달할 때 이 구조를 사용해서 응답
    */
    private Long eventId;
    private String title; //이벤트 이름
    private String description;

    private EventStatus status; // enum 타입으로 교체했으니까 , event에 종속 아님

    // 이벤트 타입
    private Event.EventType eventType;


    private Boolean isPublic;
    private Integer limitPerUser;

    private LocalDateTime startedAt;  //이벤트 시작 시간
    private LocalDateTime endedAt;  //이벤트 종료 시간

    private String thumbnailUrl;
    private List<EventOptionResDto> options;
    private Long couponId;
    private EventCouponSummaryResDto coupon;

    // 정적 팩토리 메소드, 클래스 내부에 있어야 한다 !!
    // 추후에 service,controller 안에서 (EventListResDto::from)의 형태로 사용가능하다.
    public static EventListResDto from(
            Event event,
            List<EventOptionResDto> optionDtos,
            String thumbnailUrl,
            EventStatus status
    ) {
        Long couponId = event.getCoupon() != null ? event.getCoupon().getId() : null;
        EventCouponSummaryResDto couponDto = event.getCoupon() != null
                ? EventCouponSummaryResDto.from(event.getCoupon())
                : EventCouponSummaryResDto.empty();

        return EventListResDto.builder()
                .eventId(event.getId())
                .couponId(couponId)
                .coupon(couponDto)
                .title(event.getTitle())
                .description(event.getDescription())
                .status(status)
                .eventType(event.getEventType())
                .isPublic(event.getIsPublic())
                .limitPerUser(event.getLimitPerUser())
                .startedAt(event.getStartedAt())
                .endedAt(event.getEndedAt())
                .thumbnailUrl(thumbnailUrl)
                .options(optionDtos)
                .build();
    }
}
