package com.mudosa.musinsa.coupon.domain.service;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.presentation.dto.res.CouponIssuanceResDto;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.service.CouponIssuanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class CouponIssuanceOrderTest {

    @Autowired
    private CouponIssuanceService couponIssuanceService;

    @Autowired
    private CouponRepository couponRepository;

    // 요청 및 발급 순서를 추적하는 DTO
    static class IssuanceLog {
        Long userId;
        long clickTime;        // 버튼 클릭 시간
        long requestStartTime; // 실제 요청 시작 시간 (네트워크 후)
        long issueCompleteTime; // 발급 완료 시간
        int clickOrder;        // 클릭 순서
        int issueOrder;        // 실제 발급 순서
        long networkDelay;     // 네트워크 지연 시간
        boolean success;

        @Override
        public String toString() {
            return String.format(
                    "User%-2d | 클릭: %d번째 → 발급: %d번째 | 클릭: %3dms + 지연: %3dms → 요청: %3dms → 발급완료: %3dms | %s %s",
                    userId, clickOrder, issueOrder,
                    clickTime / 1_000_000,
                    networkDelay,
                    requestStartTime / 1_000_000,
                    issueCompleteTime / 1_000_000,
                    success ? "✅" : "❌",
                    clickOrder != issueOrder ? "⚠️ 순서 역전!" : ""
            );
        }
    }

    @Test
    @DisplayName("🎯 선착순 5명 - 네트워크 지연으로 순서 뒤바뀜 확인")
    void firstComeFirstServed_networkDelay_orderReversal() throws InterruptedException {
        log.info("\n");
        log.info("=".repeat(120));
        log.info("🎫 선착순 쿠폰 이벤트 시뮬레이션");
        log.info("상황: 5명이 순서대로 버튼 클릭 → 네트워크 상태 차이로 요청 도착 순서 달라짐");
        log.info("=".repeat(120));

        // Given: 재고 5개
        Coupon coupon = createCoupon(5);
        Long couponId = coupon.getId();
        Long productId = 1L;

        List<IssuanceLog> logs = Collections.synchronizedList(new ArrayList<>());
        AtomicLong clickCounter = new AtomicLong(0);
        AtomicLong issueCounter = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);

        long testStartTime = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        // When: 5명이 10ms 간격으로 순차적으로 버튼 클릭
        for (long userId = 1; userId <= 5; userId++) {
            long finalUserId = userId;

            executor.submit(() -> {
                IssuanceLog logEntry = new IssuanceLog();
                logEntry.userId = finalUserId;

                try {
                    startLatch.await(); // 동시 시작

                    // 1️⃣ 버튼 클릭 (순차적으로)
                    Thread.sleep((finalUserId - 1) * 10); // 10ms 간격

                    logEntry.clickTime = System.nanoTime() - testStartTime;
                    logEntry.clickOrder = (int) clickCounter.incrementAndGet();

                    log.info("🖱️  User{} - 쿠폰받기 버튼 클릭! ({}ms, 클릭순서: {}번째)",
                            finalUserId,
                            logEntry.clickTime / 1_000_000,
                            logEntry.clickOrder);

                    // 2️⃣ 네트워크 지연 시뮬레이션
                    // User1: 느린 네트워크 (200ms)
                    // User2: 보통 (100ms)
                    // User3: 빠름 (30ms)
                    // User4: 아주 빠름 (10ms)
                    // User5: 보통 (80ms)
                    long[] networkDelays = {200, 100, 30, 10, 80};
                    logEntry.networkDelay = networkDelays[(int)(finalUserId - 1)];

                    log.info("📶 User{} - 네트워크 상태: {}ms 지연 중... {}",
                            finalUserId,
                            logEntry.networkDelay,
                            logEntry.networkDelay > 150 ? "🐌 (느림)" :
                                    logEntry.networkDelay < 50 ? "⚡ (빠름)" : "");


                    // 네트워크 지연 예시 , sleep상태로 전환시키기
                    Thread.sleep(logEntry.networkDelay);

                    // 3️⃣ 실제 API 요청 시작 (서버 도착)
                    logEntry.requestStartTime = System.nanoTime() - testStartTime;

                    log.info("🚀 User{} - 서버 요청 도착! ({}ms, DB 락 획득 시도...)",
                            finalUserId,
                            logEntry.requestStartTime / 1_000_000);

                    // 4️⃣ 쿠폰 발급 (비관적 락) , result는 issueCoupon api 호출을 위해 선언 debug시 사용
                    CouponIssuanceResDto result =
                            couponIssuanceService.issueCoupon(finalUserId, couponId);

                    logEntry.issueCompleteTime = System.nanoTime() - testStartTime;
                    logEntry.issueOrder = (int) issueCounter.incrementAndGet();
                    logEntry.success = true;

                    log.info("✅ User{} - 쿠폰 발급 완료! ({}ms, 발급순서: {}번째) {}",
                            finalUserId,
                            logEntry.issueCompleteTime / 1_000_000,
                            logEntry.issueOrder,
                            logEntry.clickOrder != logEntry.issueOrder ?
                                    "⚠️ [순서 역전 발생! 클릭은 " + logEntry.clickOrder + "번째였음]" : "");

                } catch (Exception e) {
                    logEntry.success = false;
                    log.error("❌ User{} - 발급 실패: {}", finalUserId, e.getMessage());
                } finally {
                    logs.add(logEntry);
                    doneLatch.countDown();
                }
            });
        }

        log.info("\n🔥 이벤트 시작!\n");
        startLatch.countDown(); // 시작!

        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 결과 분석
        printOrderAnalysis(logs);

        // 검증
        assertThat(logs).hasSize(5);
        assertThat(logs.stream().filter(l -> l.success).count()).isEqualTo(5);

        // 순서 역전 발생 확인
        long reversalCount = logs.stream()
                .filter(l -> l.clickOrder != l.issueOrder)
                .count();

        log.info("\n📌 테스트 결과: {}건의 순서 역전 발생", reversalCount);
        assertThat(reversalCount).isGreaterThan(0); // 순서 역전이 발생해야 함!
    }

    @Test
    @DisplayName("🎯 극단적 케이스 - 가장 먼저 클릭한 사람이 가장 늦게 발급")
    void extremeCase_firstClickerGetsLastIssue() throws InterruptedException {
        log.info("\n");
        log.info("=".repeat(120));
        log.info("💥 극단적 시나리오: User1이 가장 먼저 클릭했지만 네트워크가 너무 느려서 가장 늦게 발급받음");
        log.info("=".repeat(120));

        // Given
        Coupon coupon = createCoupon(3);
        Long couponId = coupon.getId();
        Long productId = 1L;

        List<IssuanceLog> logs = Collections.synchronizedList(new ArrayList<>());
        AtomicLong clickCounter = new AtomicLong(0);
        AtomicLong issueCounter = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        long testStartTime = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // User1: 가장 먼저 클릭하지만 네트워크 매우 느림 (500ms)
        executor.submit(() -> {
            IssuanceLog logEntry = new IssuanceLog();
            logEntry.userId = 1L;

            try {
                startLatch.await();

                // 즉시 클릭!
                logEntry.clickTime = System.nanoTime() - testStartTime;
                logEntry.clickOrder = (int) clickCounter.incrementAndGet();

                log.info("🖱️  User1 - 버튼 클릭! ({}ms, ⭐ 가장 먼저!)",
                        logEntry.clickTime / 1_000_000);

                logEntry.networkDelay = 500;
                log.warn("📶 User1 - 네트워크 매우 느림... {}ms 지연 🐌🐌🐌", logEntry.networkDelay);
                Thread.sleep(logEntry.networkDelay);

                logEntry.requestStartTime = System.nanoTime() - testStartTime;
                log.info("🚀 User1 - 요청 도착 ({}ms)", logEntry.requestStartTime / 1_000_000);

                CouponIssuanceResDto result =
                        couponIssuanceService.issueCoupon(1L, couponId);

                logEntry.issueCompleteTime = System.nanoTime() - testStartTime;
                logEntry.issueOrder = (int) issueCounter.incrementAndGet();
                logEntry.success = true;

                log.warn("✅ User1 - 발급 완료 ({}ms, 발급순서: {}번째) ⚠️⚠️ 가장 먼저 클릭했는데 {}번째로 발급됨!",
                        logEntry.issueCompleteTime / 1_000_000,
                        logEntry.issueOrder,
                        logEntry.issueOrder);

                logs.add(logEntry);

            } catch (Exception e) {
                log.error("실패", e);
            } finally {
                doneLatch.countDown();
            }
        });

        // User2, User3: 나중에 클릭하지만 네트워크 빠름
        for (long userId = 2; userId <= 3; userId++) {
            long finalUserId = userId;

            executor.submit(() -> {
                IssuanceLog logEntry = new IssuanceLog();
                logEntry.userId = finalUserId;

                try {
                    startLatch.await();

                    // User1보다 늦게 클릭
                    Thread.sleep(100 * (finalUserId - 1));

                    logEntry.clickTime = System.nanoTime() - testStartTime;
                    logEntry.clickOrder = (int) clickCounter.incrementAndGet();

                    log.info("🖱️  User{} - 버튼 클릭 ({}ms, User1보다 늦음)",
                            finalUserId, logEntry.clickTime / 1_000_000);

                    // 빠른 네트워크
                    logEntry.networkDelay = 20;
                    log.info("📶 User{} - 네트워크 빠름! {}ms ⚡", finalUserId, logEntry.networkDelay);
                    Thread.sleep(logEntry.networkDelay);

                    logEntry.requestStartTime = System.nanoTime() - testStartTime;
                    log.info("🚀 User{} - 요청 도착 ({}ms, User1보다 먼저 도착!)",
                            finalUserId, logEntry.requestStartTime / 1_000_000);

                    CouponIssuanceResDto result =
                            couponIssuanceService.issueCoupon(finalUserId, couponId);

                    logEntry.issueCompleteTime = System.nanoTime() - testStartTime;
                    logEntry.issueOrder = (int) issueCounter.incrementAndGet();
                    logEntry.success = true;

                    log.info("✅ User{} - 발급 완료! ({}ms, 발급순서: {}번째)",
                            finalUserId,
                            logEntry.issueCompleteTime / 1_000_000,
                            logEntry.issueOrder);

                    logs.add(logEntry);

                } catch (Exception e) {
                    log.error("실패", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        printOrderAnalysis(logs);

        // 검증: User1이 1번째로 클릭했지만 3번째로 발급받아야 함
        IssuanceLog user1Log = logs.stream()
                .filter(l -> l.userId == 1L)
                .findFirst()
                .orElseThrow();

        assertThat(user1Log.clickOrder).isEqualTo(1); // 가장 먼저 클릭
        assertThat(user1Log.issueOrder).isEqualTo(3); // 가장 늦게 발급

        log.info("\n💥 극단적 순서 역전 확인 완료!");
        log.info("👉 이것이 바로 '대기열'이 필요한 이유입니다!");
    }

    @Test
    @DisplayName("🎯 정확히 동시 클릭 - 누가 먼저 발급받을지 랜덤")
    void exactSameTime_randomOrder() throws InterruptedException {
        log.info("\n");
        log.info("=".repeat(120));
        log.info("🎲 3명이 정확히 동시에 클릭 - 누가 먼저 발급받을지는 랜덤");
        log.info("=".repeat(120));

        // Given
        Coupon coupon = createCoupon(3);
        Long couponId = coupon.getId();
        Long productId = 1L;

        List<IssuanceLog> logs = Collections.synchronizedList(new ArrayList<>());
        AtomicLong clickCounter = new AtomicLong(0);
        AtomicLong issueCounter = new AtomicLong(0);

        CountDownLatch readyLatch = new CountDownLatch(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        long testStartTime = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // When: 3명이 정확히 동시에 클릭
        for (long userId = 1; userId <= 3; userId++) {
            long finalUserId = userId;

            executor.submit(() -> {
                IssuanceLog logEntry = new IssuanceLog();
                logEntry.userId = finalUserId;

                try {
                    readyLatch.countDown();
                    startLatch.await(); // 동시 시작!

                    // 정확히 동시 클릭
                    logEntry.clickTime = System.nanoTime() - testStartTime;
                    logEntry.clickOrder = (int) clickCounter.incrementAndGet();

                    log.info("🖱️  User{} - 버튼 클릭! ({}ms, 동시!)",
                            finalUserId, logEntry.clickTime / 1_000_000);

                    // 약간의 네트워크 지연 (랜덤)
                    logEntry.networkDelay = ThreadLocalRandom.current().nextLong(10, 50);
                    log.info("📶 User{} - 네트워크 지연: {}ms", finalUserId, logEntry.networkDelay);
                    Thread.sleep(logEntry.networkDelay);

                    logEntry.requestStartTime = System.nanoTime() - testStartTime;

                    CouponIssuanceResDto result =
                            couponIssuanceService.issueCoupon(finalUserId, couponId);

                    logEntry.issueCompleteTime = System.nanoTime() - testStartTime;
                    logEntry.issueOrder = (int) issueCounter.incrementAndGet();
                    logEntry.success = true;

                    log.info("✅ User{} - 발급 완료! ({}ms, 발급순서: {}번째)",
                            finalUserId,
                            logEntry.issueCompleteTime / 1_000_000,
                            logEntry.issueOrder);

                    logs.add(logEntry);

                } catch (Exception e) {
                    log.error("실패", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        log.info("\n🔥 3명 준비 완료! 동시 클릭 시작!\n");

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        printOrderAnalysis(logs);

        log.info("\n🎲 정확히 동시에 클릭해도 네트워크/DB 경쟁 때문에 순서가 달라집니다!");
    }

    // 결과 분석 출력
    private void printOrderAnalysis(List<IssuanceLog> logs) {
        log.info("\n");
        log.info("=".repeat(120));
        log.info("📊 결과 분석");
        log.info("=".repeat(120));

        // 클릭 순서대로 정렬
        logs.sort((a, b) -> Integer.compare(a.clickOrder, b.clickOrder));

        log.info("\n[클릭 순서대로 보기]");
        log.info("-".repeat(120));
        for (IssuanceLog logEntry : logs) {
            log.info(logEntry.toString());
        }

        // 발급 순서대로 정렬
        logs.sort((a, b) -> Integer.compare(a.issueOrder, b.issueOrder));

        log.info("\n[실제 발급된 순서대로 보기]");
        log.info("-".repeat(120));
        for (IssuanceLog logEntry : logs) {
            log.info(logEntry.toString());
        }

        // 순서 역전 분석
        log.info("\n[순서 역전 상세 분석]");
        log.info("-".repeat(120));

        int reversalCount = 0;
        for (IssuanceLog logEntry : logs) {
            if (logEntry.clickOrder != logEntry.issueOrder) {
                reversalCount++;
                log.warn("⚠️  User{}: 클릭 {}번째 → 발급 {}번째 ({}칸 역전, 네트워크: {}ms)",
                        logEntry.userId,
                        logEntry.clickOrder,
                        logEntry.issueOrder,
                        Math.abs(logEntry.clickOrder - logEntry.issueOrder),
                        logEntry.networkDelay);
            } else {
                log.info("✅ User{}: 클릭 {}번째 = 발급 {}번째 (순서 유지)",
                        logEntry.userId, logEntry.clickOrder, logEntry.issueOrder);
            }
        }

        log.info("\n[요약]");
        log.info("-".repeat(120));
        log.info("총 {}명 중 {}명의 순서가 뒤바뀜 (역전율: {:.1f}%)",
                logs.size(),
                reversalCount,
                (reversalCount * 100.0 / logs.size()));

        if (reversalCount > 0) {
            log.warn("\n⚠️  비관적 락은 데이터 정합성만 보장하며, 요청 순서는 보장하지 않습니다!");
            log.warn("💡 선착순 이벤트에는 '대기열' 도입이 필요합니다!");
        } else {
            log.info("\n✅ 이번에는 순서가 유지되었지만, 매번 보장되지는 않습니다.");
        }

        log.info("=".repeat(120));
        log.info("\n");
    }

    String couponNames = "선착순 테스트 쿠폰-" + UUID.randomUUID();

    private Coupon createCoupon(Integer totalQuantity) {
        Coupon coupon = Coupon.builder()
                .couponName(couponNames)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(1000))
                .totalQuantity(totalQuantity)
                .issuedQuantity(0)
                .isActive(true)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(7))
                .build();

        return couponRepository.save(coupon);
    }
}
