package com.mudosa.musinsa.notification.repository;

import com.mudosa.musinsa.notification.dto.NotificationDTO;
import com.mudosa.musinsa.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Notification Repository
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

//    @Query("SELECT n FROM Notification n JOIN FETCH n.user JOIN FETCH n.notificationMetadata WHERE n.user.id = :userId")
//    List<Notification> findByUserId(@Param("userId")Long userId);

//    @Query("SELECT new com.mudosa.musinsa.notification.dto.NotificationDTO(" +
//            "n.notificationId, n.user.id, n.notificationMetadata.nMetadataId, " +
//            "n.notificationTitle, n.notificationMessage, n.notificationUrl, " +
//            "n.notificationStatus, n.readAt) " +
//            "FROM Notification n JOIN n.user JOIN n.notificationMetadata WHERE n.user.id = :userId")
//    List<NotificationDTO> findNotificationDTOsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT new com.mudosa.musinsa.notification.dto.NotificationDTO(" +
            "n.notificationId, n.user.id, n.notificationMetadata.nMetadataId, " +
            "n.notificationTitle, n.notificationMessage, n.notificationUrl, " +
            "n.notificationStatus, n.readAt, n.createdAt, n.updatedAt) " +
            "FROM Notification n JOIN n.user JOIN n.notificationMetadata WHERE n.user.id = :userId",
            countQuery = "SELECT count(n) FROM Notification n WHERE n.user.id = :userId")
    Page<NotificationDTO> findNotificationDTOsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.notificationStatus = true, n.readAt = CURRENT_TIMESTAMP WHERE n.notificationId = :notificationId")
    int updateNotificationStatus(@Param("notificationId") Long notificationId);
}
