package com.mudosa.musinsa.notification;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.notification.model.NotificationMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationMetadataRepositoryTest extends ServiceConfig {

    @AfterEach
    void tearDown() {
        notificationMetadataRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("`CHAT` 카테고리의 알림 메타데이터를 조회한다.")
    class findByNotificationCategory {

        @DisplayName("조회된 값이 있을 때")
        @Test
        void findByNotificationCategoryTest(){
        // given
            saveNotificationMetadata("CHAT");
            saveNotificationMetadata("RESTOCK");
            saveNotificationMetadata("STOCKLACK");
        // when
            Optional<NotificationMetadata> notificationMetadata = notificationMetadataRepository.findByNotificationCategory("CHAT");
        // then
            assertThat(notificationMetadata.orElseThrow().getNotificationCategory()).isEqualTo("CHAT");
        }

        @DisplayName("조회된 값이 없을 때")
        @Test
        void canTFindByNotificationCategoryTest(){
        // given

        // when
        Optional<NotificationMetadata> notificationMetadata = notificationMetadataRepository.findByNotificationCategory("CHAT");
        // then
        assertThat(notificationMetadata).isEmpty();
        }
    }

}
