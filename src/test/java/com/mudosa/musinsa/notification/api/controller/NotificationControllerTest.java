package com.mudosa.musinsa.notification.api.controller;


import com.mudosa.musinsa.notification.dto.NotificationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("NotificationController 테스트")
public class NotificationControllerTest extends NotificationControllerTestSupport{

    @Nested
    @DisplayName("/api/notification/{userId} GET 메서드의 호출을 받는다.")
    class readNotification{
        @DisplayName("userId를 입력하면 사용자의 알림 내역을 조회한다.")
        @Test
        void readNotificationTest() throws Exception {
            // given
            Long userId = 1L;
            NotificationDTO notificationDTO = createNotificationDTO();
            List<NotificationDTO> dtoList = List.of(notificationDTO);
            // Pageable 객체와 Page 객체를 생성합니다.
            Pageable pageable = PageRequest.of(0, 10);
            Page<NotificationDTO> pageResult = new PageImpl<>(dtoList, pageable, dtoList.size());

            // service 메서드가 userId와 Pageable 타입의 인자를 받을 때 pageResult를 반환하도록 설정합니다.
            given(notificationService.readNotification(eq(userId), any(Pageable.class)))
                    .willReturn(pageResult);

            // when & then
            // API 호출 시 page, size 파라미터를 추가합니다.
            mockMvc.perform(get("/api/notification/{userId}", userId)
                            .param("page", "0")
                            .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk());

            // service 메서드가 올바른 인자들로 호출되었는지 검증합니다.
            verify(notificationService).readNotification(eq(userId), any(Pageable.class));

        }
    }

    @Nested
    @DisplayName("/api/notification/read PATCH 메서드의 호출을 받는다.")
    class updateNotification {
        @DisplayName("사용자 1이 자신에게 온 알림 1개를 읽음 처리한다.")
        @Test
        void updateNotificationTest() throws Exception {
        // given
        NotificationDTO notificationDTO = createNotificationDTO();
        // when & then
        mockMvc.perform(patch("/api/notification/read")
                        .content(objectMapper.writeValueAsString(notificationDTO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk());
        verify(notificationService).updateNotificationState(notificationDTO.getNotificationId());
        }
    }

    private NotificationDTO createNotificationDTO() {
        return NotificationDTO.builder()
                .userId(1L)
                .notificationTitle("asdasd")
                .notificationMessage("asdasd")
                .notificationUrl("asdasd")
                .build();
    }
}
