package com.mudosa.musinsa.notification.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.user.domain.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 알림 애그리거트 루트
 * @author katsudon
 */
@Entity
@Table(name = "notification")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name="n_metadata_id")
    private NotificationMetadata notificationMetadata;

    private String notificationTitle;
    private String notificationMessage;
    private String notificationUrl;

    @Builder.Default
    private Boolean notificationStatus = false;
    private LocalDateTime readAt;


}
