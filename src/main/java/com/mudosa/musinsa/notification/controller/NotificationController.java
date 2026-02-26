package com.mudosa.musinsa.notification.controller;

import com.mudosa.musinsa.notification.dto.NotificationDTO;
import com.mudosa.musinsa.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 프론트, 혹은 Postman을 통해서 Service를 호출할 수 있는 엔드포인트 생성 클래스
 * TODO: 회원가입에 성공하면 사용자 ID와 firebase token을 받아야 한다.
 * FIXME: 프론트 로그인에 성공했음에도 jwt token issue로 Service 호출을 할 수 없는 문제 수정 필요.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * 프론트에서 http get method 를 호출하면 사용자Id를 가지고 있는 tuple을 불러서 List 형태로 return한다.
     * @param userId 사용자Id(Long)
     * @return NotificationDTO List Object
     */
    @GetMapping("/{userId}")
    public Page<NotificationDTO> readNotification(@PathVariable Long userId, Pageable pageable) {
        return notificationService.readNotification(userId, pageable);
    }

    /**
     * 사용자가 알림을 클릭하면 알림을 읽음 상태로 수정해주는 컨트롤러
     * @param notificationDTO Service에서 리턴하는 알림DTO. 프론트에서는 notificationId 값만 줍니다.
     * @return 업데이트에 성공하면 1, 아니면 다른값(?)
     */
    @PatchMapping("/read")
    public int updateNotification(@RequestBody NotificationDTO notificationDTO) {
        return notificationService.updateNotificationState(notificationDTO.getNotificationId());
    }

//    @PostMapping("/create/test")
//    public NotificationDTO createNotification(@RequestBody NotificationDTO notificationDTO) {
//        notificationService.createNotificationFromDTO(notificationDTO);
//        return notificationDTO;
//    }

}