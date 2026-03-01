package com.mudosa.musinsa.coupon.service;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.MemberCoupon;
import com.mudosa.musinsa.coupon.presentation.dto.res.CouponIssuanceResDto;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssuanceService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    // redis에서 쿠폰발급 처리할 때 사용하는 중복 발급 방지용 redis set key prefix
    private static final String ISSUED_SET_PREFIX = "coupon:issued:";
    private static final String LOCK_PREFIX = "coupon:issue:lock:";

    // 조회 전용 메서드
    @Transactional(readOnly = true)
    public Optional<CouponIssuanceResDto> findIssuedCoupon(Long userId, Long couponId) {
        return memberCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .map(existing -> CouponIssuanceResDto.duplicate(
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



    /*=======================================
                   쿠폰 발급 로직
     - 쿠폰 발급 진입점 (트랜잭션 없음)
     - 3단계 방어전략
      1. Redis Set으로 초고속 중복체크
      2. 유저별 분산락으로 동시 클릭 방지
      3. DB비관적 락으로 재고 보호
     ========================================*/

    @Transactional
    public CouponIssuanceResDto issueCoupon(Long userId, Long couponId) {

        // 1. Redis로 초고속 중복 체크
        String issueKey = ISSUED_SET_PREFIX + couponId;
        String userIdStr = userId.toString();

        Boolean isIssued = redisTemplate.opsForSet().isMember(issueKey, userIdStr);
        if (Boolean.TRUE.equals(isIssued)) {
            log.info("중복 발급 감지 (Redis 빠른 체크) - userId: {}, couponId: {}", userIdStr, couponId);

            // DB에서 확인 (있으면 반환, 없으면 계속 진행)
            Optional<CouponIssuanceResDto> existing = findIssuedCoupon(userId, couponId);
            if (existing.isPresent()) {
                return existing.get();
            }

            // Redis에는 있는데 DB에 없음 → Redis-DB 불일치 (트랜잭션 타이밍 이슈)
            log.warn("Redis-DB 불일치 감지. 발급 진행 - userId: {}, couponId: {}", userId, couponId);

        }

        // 2 . 유저별 분산 락 (같은 유저의 연속 클릭 방지)
        String lockKey = LOCK_PREFIX + userId + ":" + couponId;
        RLock userLock = redissonClient.getLock(lockKey);


        try{
            // 락 획득 시도
            boolean acquired = userLock.tryLock(3,10, TimeUnit.SECONDS);

            // 만약 락을 얻지 못했다면
            if(!acquired){
                log.info("락 획득 실패 - userId: {}, couponId: {} " , userId, couponId);
                throw new BusinessException(
                        ErrorCode.COUPON_APPLIED_FALIED,
                        "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요."
                );
            }

            //락 획득 후 Redis 재체크
            Boolean recheckIssued = redisTemplate.opsForSet().isMember(issueKey, userIdStr);
            if (Boolean.TRUE.equals(recheckIssued)) {
                log.info("중복 발급 감지 (락 후 재체크) - userId: {}, couponId: {}", userId, couponId);

                // DB 확인
                Optional<CouponIssuanceResDto> existing = findIssuedCoupon(userId, couponId);
                if (existing.isPresent()) {
                    return existing.get();
                }

                log.warn("Redis-DB 불일치 감지 (재체크). 발급 진행 - userId: {}, couponId: {}", userId, couponId);

            }

            return issueCouponWithLock(userId, couponId, issueKey, userIdStr);

        }catch (InterruptedException e){

            // 자바 멀티 스레딩
            Thread.currentThread().interrupt();
            log.error("락 대기 중 인터럽트 발생 - userId: {}, couponId: {}", userId, couponId,e);
            throw new BusinessException(
                    ErrorCode.COUPON_APPLIED_FALIED,
                    "쿠폰 발급이 중단되었습니다."
            );
        }finally {
            if (userLock.isHeldByCurrentThread()){
                userLock.unlock();
            }
        }

    }

    // issueCoupon() 트랜잭션 안에서 실행
    public CouponIssuanceResDto issueCouponWithLock(Long userId, Long couponId, String issueKey, String userIdStr) {


        // 1. 쿠폰 조회 + 비관적 락 (동시성 제어)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 2. 발급 가능 상태 검증 (활성화, 기간, 재고 확인)
        LocalDateTime now = LocalDateTime.now();
        coupon.validateIssuable(now);

        // 3.DB에서 최종 중복 발급 체크

        Optional<MemberCoupon> existing = memberCouponRepository
                .findByUserIdAndCouponId(userId, couponId);


        if (existing.isPresent()) {
            MemberCoupon mc = existing.get();

            //redis에도 동기화
            addToRedisSet(issueKey,userIdStr,coupon);
            log.info("기존 발급 재사용 - userId: {}, couponId: {}", userId, couponId);
            return CouponIssuanceResDto.duplicate(
                    mc.getId(), couponId, mc.getExpiredAt(), mc.getCreatedAt()
            );
        }


        // ✅ 4. 신규 발급
        try {
            CouponIssuanceResDto result = createMemberCoupon(userId, coupon);


            //트랜잭션 커밋 후 redis 추가
            registerRedisUpdateAfterCommit(issueKey, userIdStr, couponId);


            return result;

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
            // redis 동기화
            addToRedisSet(issueKey,userIdStr,coupon);

            return CouponIssuanceResDto.duplicate(
                    mc.getId(), couponId, mc.getExpiredAt(), mc.getCreatedAt()
            );
        }

    }

    /**
     * 트랜잭션 커밋 후 Redis 업데이트 등록
     *
     * 🎯 핵심 로직:
     * - afterCommit(): 트랜잭션 커밋 성공 후 실행
     * - afterCompletion(): 커밋 or 롤백 후 실행 (상태 확인 가능)
     */
    private void registerRedisUpdateAfterCommit(String issueKey, String userIdStr, Long couponId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            // 커밋 성공 후 쿠폰 조회 (이제 다른 트랜잭션에서도 보임)
                            Coupon coupon = couponRepository.findById(couponId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

                            // Redis에 추가
                            addToRedisSet(issueKey, userIdStr, coupon);

                            log.info("Redis 동기화 완료 (커밋 후) - userId: {}, couponId: {}",
                                    userIdStr, couponId);
                        } catch (Exception e) {
                            // Redis 추가 실패해도 DB는 이미 저장됨
                            log.error("Redis 동기화 실패 (커밋 후) - userId: {}, couponId: {}",
                                    userIdStr, couponId, e);
                        }
                    }
                }
        );
    }

    // 신규 쿠폰 발급 로직
    private CouponIssuanceResDto createMemberCoupon(Long userId, Coupon coupon) {
        // 발급 수량 증가 (재고 차감 효과: totalQuantity - issuedQuantity = 남은 재고)
        coupon.increaseIssuedQuantity();

        // 회원-쿠폰 엔티티 생성/저장
        MemberCoupon memberCoupon = MemberCoupon.issue(userId, coupon);
        MemberCoupon saved = memberCouponRepository.save(memberCoupon);

        log.info("쿠폰 발급 완료 - userId: {}, couponId: {}, 남은 재고: {}",
                userId, coupon.getId(),
                coupon.getRemainingQuantity() != null ?
                        coupon.getRemainingQuantity() : "무제한");

        return CouponIssuanceResDto.issued(
                // 흠 saved? 비동기?
                saved.getId(),
                coupon.getId(),
                saved.getExpiredAt(),
                saved.getCreatedAt()
        );
    }

     /*
     * Redis Set에 발급 기록 추가 (TTL 설정) (새로 추가)
     */

    private void addToRedisSet(String issueKey,String userIdStr,Coupon coupon){
        redisTemplate.opsForSet().add(issueKey,userIdStr);

        if (coupon.getEndDate() != null) {
            long daysUntilExpired = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    coupon.getEndDate()
            ) + 1;


            if (daysUntilExpired >0) {
                redisTemplate.expire(issueKey, daysUntilExpired, TimeUnit.DAYS);
            }
        }
    }



}
