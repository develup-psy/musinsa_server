package com.mudosa.musinsa.coupon.domain.service;

import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.coupon.service.CouponIssuanceService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mysql-test")
@Slf4j
class CouponConcurrencyTest {

    @Autowired
    private CouponIssuanceService couponIssuanceService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private DataSource dataSource;

    private Coupon testCoupon;
    private List<Long> testUsers;

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트 데이터 초기화
        memberCouponRepository.deleteAll();
        couponRepository.deleteAll();

        // 유니크 제약 조건 회피 : UUID 추가
        String uniqueCouponName = "동시성 테스트쿠폰_" + System.nanoTime();

        // 선착순 100명 쿠폰 생성
        testCoupon = Coupon.builder()
                .couponName(uniqueCouponName)           // String 값 필수
                .discountType(DiscountType.AMOUNT)              // Enum 값 필수
                .discountValue(new BigDecimal("10000"))         // BigDecimal 값 필수
                .startDate(LocalDateTime.now().minusDays(1))    // LocalDateTime 필수
                .endDate(LocalDateTime.now().plusDays(30))      // LocalDateTime 필수
                .totalQuantity(300)                              // Integer (선택)
                // issuedQuantity와 isActive는 @Builder.Default로 자동 설정되므로 생략 가능
                .build();

        testCoupon = couponRepository.save(testCoupon);

        // 1000명의 테스트 유저 ID 생성
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 30000; i++) {
            testUsers.add((long) i);
        }

        log.info("===== 테스트 준비 완료 =====");
        log.info("쿠폰 ID: {}", testCoupon.getId());
        log.info("초기 재고: {}", testCoupon.getTotalQuantity());
        log.info("테스트 유저 수: {}", testUsers.size());
        log.info("========================");
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 1000명 요청, 100명만 성공")
    void ConcurrencyTest() throws InterruptedException {
        // Given
        int threadCount = 30000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 결과 집계용
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger lockFailureCount = new AtomicInteger(0);
        AtomicInteger unknownErrorCount = new AtomicInteger(0);

        ConcurrentHashMap<String, AtomicInteger> errorMessages = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        // ✅ 테스트 시작 직전 상태 확인
        log.info("\n========== 테스트 시작 전 상태 ==========");
        printInnoDBStatus();

        // When - 1000명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int threadNumber = i + 1;
            final Long userId = testUsers.get(i);

            executorService.submit(() -> {
                try {
                    couponIssuanceService.issueCoupon(userId, testCoupon.getId());
                    successCount.incrementAndGet();
                    log.debug("Thread {} (User {}) - 발급 성공", threadNumber, userId);

                } catch (DataIntegrityViolationException e) {
                    // 유니크 제약 위반 (중복 발급 시도)
                    duplicateCount.incrementAndGet();
                    log.debug("Thread {} (User {}) - 중복 발급 차단", threadNumber, userId);

                } catch (JpaSystemException e) {
                    // 락 타임아웃 등
                    lockFailureCount.incrementAndGet();
                    log.debug("Thread {} (User {}) - 락 실패: {}", threadNumber, userId, e.getMessage());

                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("재고")) {
                        outOfStockCount.incrementAndGet();
                        log.debug("Thread {} (User {}) - 재고 소진", threadNumber, userId);
                    } else {
                        unknownErrorCount.incrementAndGet();
                        errorMessages.computeIfAbsent(errorMsg, k -> new AtomicInteger(0))
                                .incrementAndGet();
                        log.warn("Thread {} (User {}) - 예외 발생: {}", threadNumber, userId, errorMsg);
                    }

                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        log.info("\n========== 처리 대기 중 (30초) - 이 시점에 별도 터미널에서 SHOW ENGINE INNODB STATUS 확인 가능 ==========\n");

        // ✅ 중간 모니터링 (5초 후)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("\n========== 5초 경과 - 중간 상태 확인 ==========");
                printInnoDBStatus();
                printActiveTransactions();
            } catch (Exception e) {
                log.error("중간 모니터링 실패", e);
            }
        }).start();

        latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // ✅ 테스트 완료 후 최종 상태
        log.info("\n========== 테스트 완료 후 최종 상태 ==========");
        // MySQL에서만 InnoDB 상태 조회 (H2에서는 에러 발생)
        if (isMySQLDatabase()) {
            printInnoDBStatus();
            printDeadlockLog();
        } else {
            log.warn("H2 데이터베이스에서는 InnoDB 상태 조회를 지원하지 않습니다.");
            log.warn("MySQL로 테스트하려면 @ActiveProfiles(\"mysql-test\")를 사용하세요.");
        }

        // 결과 검증
        Long actualIssuedCount = memberCouponRepository.countByCouponId(testCoupon.getId());
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

        // 결과 출력
        log.info("\n");
        log.info("========== 테스트 결과 ==========");
        log.info("실행 시간: {}ms", duration);
        log.info("총 요청 수: {}", threadCount);
        log.info("발급 성공: {}", successCount.get());
        log.info("재고 소진: {}", outOfStockCount.get());
        log.info("중복 차단: {}", duplicateCount.get());
        log.info("락 실패: {}", lockFailureCount.get());
        log.info("기타 오류: {}", unknownErrorCount.get());
        log.info("================================");
        log.info("DB 실제 발급 수: {}", actualIssuedCount);
        log.info("쿠폰 issuedQuantity: {}", updatedCoupon.getIssuedQuantity());
        log.info("쿠폰 남은 재고: {}", updatedCoupon.getRemainingQuantity());
        log.info("================================\n");

        // 에러 메시지 상세
        if (!errorMessages.isEmpty()) {
            log.info("========== 에러 상세 ==========");
            errorMessages.forEach((msg, count) ->
                    log.info("{}: {} 건", msg, count.get())
            );
            log.info("================================\n");
        }

        // 검증
        assertThat(actualIssuedCount).isEqualTo(300);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(300);
        assertThat(successCount.get()).isEqualTo(300);
        assertThat(successCount.get() + outOfStockCount.get() + duplicateCount.get()
                + lockFailureCount.get() + unknownErrorCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("동일 유저 중복 발급 방지 테스트")
    void SameUserPrventTest() throws InterruptedException {
        // Given
        int threadCount = 100;
        Long sameUserId = 1L;  // 같은 유저
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When - 같은 유저가 100번 요청
        for (int i = 0; i < threadCount; i++) {
            final int threadNumber = i + 1;
            executorService.submit(() -> {
                try {
                    var result = couponIssuanceService.issueCoupon(sameUserId, testCoupon.getId());
                    if (result.duplicate()) {
                        duplicateCount.incrementAndGet();
                        log.debug("Thread {} - 기존 발급 재사용", threadNumber);
                    } else {
                        successCount.incrementAndGet();
                        log.debug("Thread {} - 신규 발급", threadNumber);
                    }
                } catch (Exception e) {
                    log.error("Thread {} - 오류: {}", threadNumber, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        Long issuedCount = memberCouponRepository.countByUserIdAndCouponId(sameUserId, testCoupon.getId());

        log.info("\n========== 중복 방지 테스트 결과 ==========");
        log.info("신규 발급: {}", successCount.get());
        log.info("중복 차단: {}", duplicateCount.get());
        log.info("DB 실제 발급 수: {}", issuedCount);
        log.info("========================================\n");

        assertThat(issuedCount).isEqualTo(1);  // 한 번만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(99);
    }




    /*
    *
    * 로그 발급을 위한 메서드들
    *
    * */

    /**
     * MySQL 데이터베이스인지 확인
     */
    private boolean isMySQLDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            return productName.toLowerCase().contains("mysql");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * InnoDB 엔진 상태 조회
     */
    private void printInnoDBStatus() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {

            if (rs.next()) {
                String status = rs.getString("Status");

                log.info("\n");
                log.info("========== INNODB STATUS (요약) ==========");

                // 트랜잭션 섹션 추출
                extractSection(status, "TRANSACTIONS", 30);

                // 락 정보 추출
                extractSection(status, "LATEST DETECTED DEADLOCK", 50);

                log.info("=========================================\n");

                // 전체 상태를 파일로 저장하고 싶다면
                // Files.writeString(Path.of("innodb_status.log"), status);
            }
        } catch (Exception e) {
            log.error("InnoDB 상태 조회 실패", e);
        }
    }

    /**
     * 활성 트랜잭션 조회
     */
    private void printActiveTransactions() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM information_schema.INNODB_TRX")) {

            log.info("\n========== 활성 트랜잭션 ==========");
            int count = 0;
            while (rs.next()) {
                count++;
                log.info("TRX #{}", count);
                log.info("  ID: {}", rs.getString("trx_id"));
                log.info("  상태: {}", rs.getString("trx_state"));
                log.info("  시작: {}", rs.getTimestamp("trx_started"));
                log.info("  쿼리: {}", rs.getString("trx_query"));
                log.info("---");
            }
            if (count == 0) {
                log.info("활성 트랜잭션 없음");
            }
            log.info("==================================\n");
        } catch (Exception e) {
            log.error("활성 트랜잭션 조회 실패", e);
        }
    }

    /**
     * 데드락 로그 조회
     */
    private void printDeadlockLog() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {

            if (rs.next()) {
                String status = rs.getString("Status");

                if (status.contains("LATEST DETECTED DEADLOCK")) {
                    log.info("\n");
                    log.info("========== 데드락 감지 ==========");
                    extractSection(status, "LATEST DETECTED DEADLOCK", 100);
                    log.info("=================================\n");
                } else {
                    log.info("데드락이 감지되지 않았습니다.");
                }
            }
        } catch (Exception e) {
            log.error("데드락 로그 조회 실패", e);
        }
    }

    /**
     * InnoDB 상태에서 특정 섹션 추출
     */
    private void extractSection(String status, String sectionName, int lineLimit) {
        String[] lines = status.split("\n");
        boolean inSection = false;
        int lineCount = 0;

        for (String line : lines) {
            if (line.contains(sectionName)) {
                inSection = true;
            }

            if (inSection) {
                log.info(line);
                lineCount++;

                if (lineCount >= lineLimit) {
                    break;
                }
            }
        }
    }
}