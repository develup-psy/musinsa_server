package com.mudosa.musinsa.coupon.domain.service;

import com.mudosa.musinsa.coupon.domain.model.Coupon;
import com.mudosa.musinsa.coupon.domain.model.CouponProduct;
import com.mudosa.musinsa.coupon.domain.model.MemberCoupon;
import com.mudosa.musinsa.coupon.domain.repository.CouponRepository;
import com.mudosa.musinsa.coupon.domain.repository.MemberCouponRepository;
import com.mudosa.musinsa.coupon.domain.service.CouponProductService;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CouponIssuanceService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final CouponProductService couponProductService;

    // 조회 전용
    @Transactional(readOnly = true)
    public Optional<CouponIssuanceResult> findIssuedCoupon(Long userId, Long couponId) {
        return memberCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .map(existing -> CouponIssuanceResult.duplicate(
                        existing.getId(),
                        couponId,
                        existing.getExpiredAt(),
                        existing.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public long countIssuedByUser(Long userId, Long couponId) {
        return memberCouponRepository.countByUserIdAndCouponId(userId, couponId);
    }

    // 실제 발급 로직 전용

    @Transactional
    public CouponIssuanceResult issueCoupon(Long userId, Long couponId,Long productId){

        // 1. 쿠폰 조회 + 비관적 락 (동시성 제어)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 2. 발급 가능 상태 검증 (활성화, 기간, 재고 확인)
        LocalDateTime now = LocalDateTime.now();
        coupon.validateIssuable(now);


        // 3. 중복 발급 체크
        Optional<MemberCoupon> existing = memberCouponRepository
                .findByUserIdAndCouponId(userId, couponId);

        if (existing.isPresent()) {
            MemberCoupon mc = existing.get();
            log.info("기존 발급 재사용 - userId: {}, couponId: {}", userId, couponId);
            return CouponIssuanceResult.duplicate(
                    mc.getId(), couponId, mc.getExpiredAt(), mc.getCreatedAt()
            );
        }

        // ✅ 4. 신규 발급
        try {
            return createMemberCoupon(userId, coupon);
        } catch (DataIntegrityViolationException e) {
            // unique 제약 위반 시 (동시 발급 충돌)
            // 이미 발급된 쿠폰을 조회해서 반환
            log.warn("중복 발급 시도 감지 (Unique 제약 위반) - userId: {}, couponId: {}",
                    userId, couponId);

            MemberCoupon mc = memberCouponRepository
                    .findByUserIdAndCouponId(userId, couponId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.COUPON_APPLIED_FALIED,
                            "쿠폰 발급 중 오류가 발생했습니다"
                    ));

            return CouponIssuanceResult.duplicate(
                    mc.getId(), couponId, mc.getExpiredAt(), mc.getCreatedAt()
            );
        }




//        return memberCouponRepository.findByUserIdAndCouponId(userId, couponId)
//                .map(mc -> {
//                    log.info("기존 발급 재사용 - userId: {}, couponId: {}", userId, couponId);
//                    return CouponIssuanceResult.duplicate(
//                            mc.getId(), couponId, mc.getExpiredAt(), mc.getCreatedAt()
//                    );
//                })
//                .orElseGet(() -> createMemberCoupon(userId,coupon));

    }

    // 신규 쿠폰 발급 로직
    private CouponIssuanceResult createMemberCoupon(Long userId, Coupon coupon) {
        // 발급 수량 증가 (재고 차감 효과: totalQuantity - issuedQuantity = 남은 재고)
        coupon.increaseIssuedQuantity();

        // 회원-쿠폰 엔티티 생성/저장
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);
        MemberCoupon saved = memberCouponRepository.save(memberCoupon);

        log.info("쿠폰 발급 완료 - userId: {}, couponId: {}, 남은 재고: {}",
                userId, coupon.getId(),
                coupon.getRemainingQuantity() != null ?
                    coupon.getRemainingQuantity() : "무제한");

        return CouponIssuanceResult.issued(
                // 흠 saved? 비동기?
                saved.getId(),
                coupon.getId(),
                saved.getExpiredAt(),
                saved.getCreatedAt()
        );
    }


    // 결과 DTO ? => 따로 빼야 하나 ? => 서비스 내부에서만 쓰임
    public record CouponIssuanceResult(Long memberCouponId,
                                       Long couponId,
                                       LocalDateTime expiredAt,
                                       LocalDateTime issuedAt,
                                       boolean duplicate) {
        public static CouponIssuanceResult issued(Long memberCouponId,
                                               Long couponId,
                                               LocalDateTime expiredAt,
                                               LocalDateTime issuedAt) {
            return new CouponIssuanceResult(memberCouponId, couponId, expiredAt, issuedAt,false);
        }
        public static CouponIssuanceResult duplicate(Long memberCouponId,
                                                      Long couponId,
                                                      LocalDateTime expiredAt,
                                                      LocalDateTime issuedAt) {
            return new CouponIssuanceResult(memberCouponId, couponId, expiredAt, issuedAt, true);
        }
    }
}

