package com.mudosa.musinsa.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO implements Serializable {
    private Long notificationId;
    private Long userId;
    private Integer nMetadataId;
    private String notificationTitle;
    private String notificationMessage;
    private String notificationUrl;
    private Boolean notificationStatus;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
