package com.mudosa.musinsa.notification;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.notification.dto.NotificationDTO;
import com.mudosa.musinsa.notification.model.Notification;
import com.mudosa.musinsa.notification.model.NotificationMetadata;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("알림 레포지토리 테스트")
@Transactional
class NotificationRepositoryTest extends ServiceConfig {

    private User saveUser(String userName){
        User user = User.create(userName, "yong1234!", "test@test.com", UserRole.USER, "http://mudosa/uploads/avatar/avatar1.png", "010-0000-0000", "서울 강남구");
        userRepository.save(user);
        return user;
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        notificationMetadataRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("어떤 사용자의 알림 내역을 조회한다.")
    class FindByUserId {

        @DisplayName("사용자 1의 알림 내역을 조회한다.")
        @Test
        void FindByUserIdTest(){
        // given
            User user1 = saveUser("user1");
            User user2 = saveUser("user2");

            NotificationMetadata notificationMetadata = saveNotificationMetadata("CHAT");

            saveNotification(user1, notificationMetadata);
            saveNotification(user1, notificationMetadata);
            saveNotification(user1, notificationMetadata);
            saveNotification(user2, notificationMetadata);
            saveNotification(user2, notificationMetadata);
            Pageable pageable = PageRequest.of(0, 10);
        // when
            Page<NotificationDTO> notifications = notificationRepository.findNotificationDTOsByUserId(user1.getId(),pageable);
        // then
            assertThat(notifications).hasSize(3);

        }
    }

    @Nested
    @DisplayName("어떤 사용자의 알림 내역을 읽음으로 처리한다.")
    class UpdateNotificationStatus{

        @DisplayName("사용자1의 3개 알림 중 1개 상태를 읽음으로 수정한다.")
        @Test
        void updateNotificationStatusTest(){
        // given
        User user1 = saveUser("user1");

        NotificationMetadata notificationMetadata = saveNotificationMetadata("CHAT");

        Notification notification1 = saveNotification(user1, notificationMetadata);
        saveNotification(user1, notificationMetadata);
        saveNotification(user1, notificationMetadata);
        saveNotification(user1, notificationMetadata);
        saveNotification(user1, notificationMetadata);
        // when
            int updateCounter = notificationRepository.updateNotificationStatus(notification1.getNotificationId());
        // then
            assertThat(updateCounter).isEqualTo(1);
        }
    }
}
