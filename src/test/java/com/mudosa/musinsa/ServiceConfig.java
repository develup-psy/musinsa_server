package com.mudosa.musinsa;

import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.domain.chat.repository.ChatPartRepository;
import com.mudosa.musinsa.domain.chat.repository.ChatRoomRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageAttachmentRepository;
import com.mudosa.musinsa.domain.chat.repository.MessageRepository;
import com.mudosa.musinsa.notification.model.Notification;
import com.mudosa.musinsa.notification.model.NotificationMetadata;
import com.mudosa.musinsa.notification.repository.NotificationMetadataRepository;
import com.mudosa.musinsa.notification.repository.NotificationRepository;
import com.mudosa.musinsa.notification.service.FcmService;
import com.mudosa.musinsa.security.JwtTokenProvider;
import com.mudosa.musinsa.settlement.batch.job.DailySettlementAggregationJob;
import com.mudosa.musinsa.settlement.batch.job.MonthlySettlementAggregationJob;
import com.mudosa.musinsa.settlement.batch.job.WeeklySettlementAggregationJob;
import com.mudosa.musinsa.settlement.batch.scheduler.SettlementBatchScheduler;
import com.mudosa.musinsa.settlement.domain.repository.SettlementDailyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementMonthlyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementPerTransactionMapper;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 공통 테스트 설정 분리
@Slf4j
@ActiveProfiles("test")
@SpringBootTest(
    classes = ServerApplication.class,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
    }
)
@ImportAutoConfiguration(exclude = {
    MybatisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration.class
})
public abstract class ServiceConfig {

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  /**
   * 배치 문제 해결을 위한 임시 조치
   */
  @MockitoBean
  private SettlementDailyMapper settlementDailyMapper;
  @MockitoBean
  private SettlementMonthlyMapper settlementMonthlyMapper;
  @MockitoBean
  private SettlementPerTransactionMapper settlementPerTransactionMapper;

  @MockitoBean
  private SettlementBatchScheduler settlementBatchScheduler;
  @MockitoBean
  private DailySettlementAggregationJob dailySettlementAggregationJob;
  @MockitoBean
  private MonthlySettlementAggregationJob monthlySettlementAggregationJob;
  @MockitoBean
  private WeeklySettlementAggregationJob weeklySettlementAggregationJob;

  @MockitoBean
  private JobRepository jobRepository;
  @MockitoBean
  protected FcmService fcmService;

  @Autowired
  protected ChatRoomRepository chatRoomRepository;
  @Autowired
  protected ChatPartRepository chatPartRepository;
  @Autowired
  protected UserRepository userRepository;
  @Autowired
  protected BrandRepository brandRepository;
  @Autowired
  protected MessageRepository messageRepository;
  @Autowired
  protected MessageAttachmentRepository attachmentRepository;
  @Autowired
  protected NotificationMetadataRepository notificationMetadataRepository;
  @Autowired
  protected NotificationRepository notificationRepository;

  protected NotificationMetadata saveNotificationMetadata(String notificationCategory) {
      return notificationMetadataRepository.save(
              NotificationMetadata.builder()
                      .notificationCategory(notificationCategory)
                      .build()
      );
  }

  protected Notification saveNotification(User user, NotificationMetadata notificationMetadata){
      return notificationRepository.save(Notification.builder()
              .user(user)
              .notificationMetadata(notificationMetadata)
              .build());
  }
}
