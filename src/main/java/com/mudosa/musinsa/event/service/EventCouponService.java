package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.DiscountType;
import com.mudosa.musinsa.coupon.domain.service.CouponIssuanceService;
import com.mudosa.musinsa.event.model.Event;

import com.mudosa.musinsa.event.model.EventOption;

import com.mudosa.musinsa.event.repository.EventOptionRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.model.ProductOption;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j

public class EventCouponService {

    private final EventOptionRepository eventOptionRepository;
    private final CouponIssuanceService couponIssuanceService;


    /*

    * 이벤트 상태 및 사용자 제한 검증
    * 혼잡 시 진입 제한/큐 처리/TTL 관리 등을 캡슐화
    * 이벤트 컨텍스트에서의 쿠폰 발급 조율

    * */

    // 이벤트 쿠폰 발급 => 쿠폰 발급 버튼을 눌렀을 때 실행
    @Transactional
    public EventCouponIssueResult issueCoupon(Long eventId,
                                              Long userId ){


            // 1. 이벤트 옵션 조회 락 불필요(읽기 전용) , 이벤트와 1:1 매핑
            EventOption eventOption = eventOptionRepository.findByEventIdWithDetails(eventId)
                    // 레포에 생성 필요 -1
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            Event event = eventOption.getEvent();

            // 2. 이벤트 상태 검증( 진행중인지 )
            validateEventState(event);

            // 3. 쿠폰 정보 확인
            Coupon coupon = Optional.ofNullable(event.getCoupon())
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_COUPON_NOT_ASSIGNED));

            Long couponId = coupon.getId();

            //  4. 사용자별 제한 확인
            validateUserLimit(event, coupon, userId);

            // 상품 ID 확인
            Long productId = resolveProductId(eventOption);

            //  5. 멱등성 체크 : 이미 발급된 쿠폰이 있는지 먼저 조회 , 있으면 그대로 재사용

            Optional<CouponIssuanceService.CouponIssuanceResult> existing = couponIssuanceService
                    .findIssuedCoupon(userId, couponId);
            if (existing.isPresent()) {
                log.info("멱등 처리 - eventId: {}, userId: {}", eventId, userId);
                return EventCouponIssueResult.from(existing.get());
            }



            // 6. 실제 쿠폰 발급 ! => 쿠폰 도메인 책임
            /*
             *✅ CouponIssuanceService 내부에서 Coupon에 PESSIMISTIC_WRITE 락을 걸어
             *issuedQuantity 증가를 원자적으로 처리
            */
            CouponIssuanceService.CouponIssuanceResult issuanceResult = couponIssuanceService
                    .issueCoupon(userId, couponId, productId);


            // 10. 중복 발급 감지 ( 재진입 케이스 )
            if(issuanceResult.duplicate()){
                log.info("중복 발급 감지 - eventId: {}, userId: {}", eventId, userId);
                return EventCouponIssueResult.from(issuanceResult);
            }

            // 발급 성공 로그
            log.info("쿠폰 발급 성공 - eventId: {}, userId: {}, couponId: {}",
                    eventId, userId, couponId);

            return EventCouponIssueResult.from(issuanceResult);
        }


    // 현재 진행하고 있는 유효한 이벤트인지
    private void validateEventState(Event event) {
        LocalDateTime now = LocalDateTime.now();
        if (!event.isOngoing(now)) {
            throw new BusinessException(ErrorCode.EVENT_NOT_OPEN);
        }
    }

    @Transactional(readOnly = true)
    public EventCouponInfoResult getEventCoupon(Long eventId) {
        EventOption eventOption = eventOptionRepository.findByEventIdWithDetails(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        Event event = eventOption.getEvent();
        Coupon coupon = Optional.ofNullable(event.getCoupon())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_COUPON_NOT_ASSIGNED));

        return EventCouponInfoResult.from(event, coupon);
    }

    // 이벤트 발급 이력 테이블 (X) => MemberCoupon 테이블을 기준으로 발급 추적
    // 한 이벤트 옵션 기준으로 쿠폰을 사용한다는 전제로 사용자 검증
    private void validateUserLimit(Event event, Coupon coupon, Long userId) {

        long issuedCount = couponIssuanceService.countIssuedByUser(userId,coupon.getId()); // couponIssuance 서비스에 새로생성필요
        if (issuedCount >= event.getLimitPerUser()) {
            throw new BusinessException(ErrorCode.EVENT_USER_LIMIT_EXCEEDED);
        }
    }


    // 이벤트에(쿠폰) 매핑된 상품 ID 조회
    private Long resolveProductId(EventOption eventOption) {
        ProductOption productOption = eventOption.getProductOption();
        if(productOption == null || productOption.getProduct() == null) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }
        return productOption.getProduct().getProductId();
    }

    public record EventCouponIssueResult(Long memberCouponId,
                                        Long couponId,
                                        LocalDateTime issuedAt,
                                        LocalDateTime expiredAt,
                                        boolean duplicate ) {
        private static EventCouponIssueResult from(CouponIssuanceService.CouponIssuanceResult result){
            return new EventCouponIssueResult(
                    result.memberCouponId(),
                    result.couponId(),
                    result.issuedAt(),
                    result.expiredAt(),
                    result.duplicate()
            );
        }
    }

    public record EventCouponInfoResult(Long couponId,
                                        String couponName,
                                        DiscountType discountType,
                                        BigDecimal discountValue,
                                        BigDecimal minOrderAmount,
                                        BigDecimal maxDiscountAmount,
                                        Integer totalQuantity,
                                        Integer issuedQuantity,
                                        Integer remainingQuantity,
                                        LocalDateTime startedAt,
                                        LocalDateTime endedAt,
                                        Integer limitPerUser) {

        private static EventCouponInfoResult from(Event event, Coupon coupon) {
            return new EventCouponInfoResult(
                    coupon.getId(),
                    coupon.getCouponName(),
                    coupon.getDiscountType(),
                    coupon.getDiscountValue(),
                    coupon.getMinOrderAmount(),
                    coupon.getMaxDiscountAmount(),
                    coupon.getTotalQuantity(),
                    coupon.getIssuedQuantity(),
                    coupon.getRemainingQuantity(),
                    coupon.getStartDate(),
                    coupon.getEndDate(),
                    event.getLimitPerUser()
            );
        }
    }
}
