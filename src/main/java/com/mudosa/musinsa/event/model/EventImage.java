package com.mudosa.musinsa.event.model;

import com.mudosa.musinsa.common.domain.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이벤트 이미지 엔티티
 * Event 애그리거트 내부
 */
@Entity
@Table(
        name = "event_image",
        indexes = {
                @Index(name = "idx_evtimg_event", columnList = "event_id"),
                @Index(name = "idx_evtimg_is_thumbnail", columnList = "is_thumbnail") // 썸네일 여부 인덱스 추가
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "is_thumbnail", nullable = false)
    @Builder.Default
    private Boolean isThumbnail = false;  // 썸네일 여부를 나타내는 필드

    /** Event 할당 (Event 애그리거트에서만 호출) */
    public void assignEvent(Event event) {
        this.event = event;
    }
}
