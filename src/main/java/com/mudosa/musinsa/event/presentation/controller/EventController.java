package com.mudosa.musinsa.event.presentation.controller;

import com.mudosa.musinsa.event.model.Event;

import com.mudosa.musinsa.event.presentation.dto.req.EventCouponIssueReqDto;
import com.mudosa.musinsa.event.presentation.dto.res.EventCouponInfoResDto;
import com.mudosa.musinsa.event.presentation.dto.res.EventCouponIssueResDto;
import com.mudosa.musinsa.event.presentation.dto.res.EventListResDto;
import com.mudosa.musinsa.event.service.EventCouponService;
import com.mudosa.musinsa.event.service.EventService;

import com.mudosa.musinsa.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


import java.util.List;

//MVC
// view -> 정적 파일을 서빙하는 역할
//@Controller -> ViewResolver
@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
@Slf4j


public class EventController {

    private final EventService eventService;
    private final EventCouponService eventCouponService;



    /*
    이벤트 목록 조회 api
    @param type ( drop, comment )
    @return 필터링된 이벤트 목록 + 특정 이벤트에 포함된 상품 list
    */

    @GetMapping
    public ResponseEntity<List<EventListResDto>> getEventList(
            @RequestParam(value = "type", defaultValue = "DROP") Event.EventType type

    ) {

        log.info("이벤트 목록 조회 요청 - type: {}", type);
        List<EventListResDto> eventList = eventService.getEventListByType(type); // EventService에 만들어야됨
        return ResponseEntity.ok(eventList);
    }

    /* 쿠폰 발급 트리거
    * 슬롯/상태 검증 → (eventId,couponId) 재고 차감 → 발급이력 저장 → member_coupon 생성
    * 멱등성 보장 ? 이미 발급받은 경우 200으로 기존 결과 반환 or 409로 충돌
    */

    @GetMapping("/{eventId}/coupons")
    public ResponseEntity<EventCouponInfoResDto> getEventCoupon(@PathVariable Long eventId) {
        EventCouponService.EventCouponInfoResult result = eventCouponService.getEventCoupon(eventId);
        return ResponseEntity.ok(EventCouponInfoResDto.from(result));
    }

    @PostMapping("/{eventId}/coupons/issue")
    public ResponseEntity<EventCouponIssueResDto> issueCoupon(
            @PathVariable Long eventId,
            @Valid @RequestBody EventCouponIssueReqDto request,
            @AuthenticationPrincipal CustomUserDetails user

    ) {

        log.info("쿠폰 발급 요청 - eventId: {}, userId: {}", eventId, user != null ? user.getUserId() : "null");
        log.info("Request body - productOptionId: {}", request.getProductOptionId());


        EventCouponService.EventCouponIssueResult result = eventCouponService.issueCoupon(

                        eventId,
                        user.getUserId()

                //이벤트 id, 이벤트상품옵션 id, 사용자 id
        );

        EventCouponIssueResDto response = EventCouponIssueResDto.from(result);
        return ResponseEntity.ok(response);
    }



}
