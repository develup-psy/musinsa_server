package com.mudosa.musinsa.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NoteMetaDTO {
    private Integer nMetadataId;
    private String notificationTitle;
    private String notificationMessage;
    private String notificationType;
    private String notificationCategory;
    private String notificationUrl;
}
