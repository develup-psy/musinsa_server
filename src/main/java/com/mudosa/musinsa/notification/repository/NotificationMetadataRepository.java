package com.mudosa.musinsa.notification.repository;

import com.mudosa.musinsa.notification.model.NotificationMetadata;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * NotificationMetadata Repository
 */
@Repository
public interface NotificationMetadataRepository extends JpaRepository<NotificationMetadata, Long> {
    
    Optional<NotificationMetadata> findByNotificationCategory(String notificationCategory);

    @Transactional
    @Modifying
    void deleteById(Long id);
}
