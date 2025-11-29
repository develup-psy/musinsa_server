package com.mudosa.musinsa.notification.domain.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.mudosa.musinsa.domain.chat.entity.ChatPart;
import com.mudosa.musinsa.domain.chat.repository.ChatPartRepository;
import com.mudosa.musinsa.fbtoken.service.FirebaseTokenService;
import com.mudosa.musinsa.notification.domain.dto.NotificationDTO;
import com.mudosa.musinsa.notification.domain.event.ChatNotificationCreatedEvent;
import com.mudosa.musinsa.notification.domain.model.Notification;
import com.mudosa.musinsa.notification.domain.model.NotificationMetadata;
import com.mudosa.musinsa.notification.domain.repository.NotificationMetadataRepository;
import com.mudosa.musinsa.notification.domain.repository.NotificationRepository;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 필요한 기능
 * 1. 어떤 사용자의 알림 목록 열람
 * 2. 어떤
 */

@Slf4j
@Service
@AllArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMetadataRepository notificationMetadataRepository;
    private final FcmService fcmService;
    private final FirebaseTokenService firebaseTokenService;
    private final ChatPartRepository chatPartRepository;

    private final String CHAT_METADATA_CATEGORY = "CHAT";
    private final String MESSAGE_FROM_CHAT_ROOM = "채팅방에서 메세지가 왔습니다.";
    private final String ATTACHED_FILE = "첨부파일이 있습니다";
    private final String CHAT_URL = "/chat/";

    public List<NotificationDTO> readNotification(Long userId){
        return notificationRepository.findByUserId(userId).stream()
                .map(notification -> NotificationDTO.builder()
                        .notificationId(notification.getNotificationId())
                        .userId(notification.getUser().getId())
                        .nMetadataId(notification.getNotificationMetadata().getNMetadataId())
                        .notificationTitle(notification.getNotificationTitle())
                        .notificationMessage(notification.getNotificationMessage())
                        .notificationUrl(notification.getNotificationUrl())
                        .notificationStatus(notification.getNotificationStatus())
                        .readAt(notification.getReadAt())
                        .build())
                        .toList();
    }

    public List<Notification> createChatNotification(ChatNotificationCreatedEvent chatNotificationCreatedEvent){

        List<ChatPart> chatPartList = chatPartRepository.findChatPartsExcludingUser(chatNotificationCreatedEvent.getUserId(), chatNotificationCreatedEvent.getChatId());
        NotificationMetadata chatNotificationMetadata = notificationMetadataRepository.findByNotificationCategory(CHAT_METADATA_CATEGORY).orElseThrow(
                ()->new NoSuchElementException("Notification Metadata not found")
        );

        List<Long> userIds = chatPartList.stream()
                .map(chatPart -> chatPart.getUser().getId())
                .toList();

        String message = chatNotificationCreatedEvent.getContent();
        List<Notification> notificationList = chatPartList.stream()
                .map(chatPart->Notification.builder()
                        .user(chatPart.getUser())
                        .notificationMetadata(chatNotificationMetadata)
                        .notificationTitle(chatPart.getChatRoom().getBrand().getNameKo() + MESSAGE_FROM_CHAT_ROOM)
                        .notificationMessage(Objects.isNull(message)?ATTACHED_FILE:message)
                        .notificationUrl(CHAT_URL + chatPart.getChatRoom().getChatId()+"/")
                        .build())
                        .toList();

        List<Notification> notifications = notificationRepository.saveAll(notificationList);

        if (fcmService != null && !notificationList.isEmpty()) {
            fcmService.sendMessageByToken(notificationList.getFirst().getNotificationTitle(), message,firebaseTokenService.readFirebaseTokens(userIds));
        } else {
            log.info("알림 생성 중 문제가 발생했습니다.");
            return null;
        }
        return notifications;
    }

    @Transactional
    public int updateNotificationState(Long notificationId){
        return notificationRepository.updateNotificationStatus(notificationId);
    }

//    public void createNotification(Long userId,String notificationCategory) throws FirebaseMessagingException {
//
//        User resultUser = userRepository.findById(userId).orElseThrow(
//                ()->new NoSuchElementException("User not found")
//        );
//
//        NotificationMetadata resultNotificationMetadata = notificationMetadataRepository.findByNotificationCategory(notificationCategory).orElseThrow(
//                ()->new NoSuchElementException("Notification Metadata not found")
//        );
//
//        Notification notification = Notification.builder()
//                .user(resultUser)
//                .notificationMetadata(resultNotificationMetadata)
//                .notificationTitle(resultNotificationMetadata.getNotificationTitle())
//                .notificationMessage(resultNotificationMetadata.getNotificationMessage())
//                .notificationUrl(resultNotificationMetadata.getNotificationUrl())
//                .build();
//        notificationRepository.save(notification);
//        //푸시 알림 보내기
//        if (fcmService != null) {
//            fcmService.sendMessageByToken(notification.getNotificationTitle(),notification.getNotificationMessage(),firebaseTokenService.readFirebaseTokens(List.of(resultUser.getId())));
//        } else {
//            log.info("FCM이 비활성화되어 있습니다. 푸시 알림을 전송하지 않습니다.");
//        }
//    }


//    public void createOutOfStockNote(Inventory inventory){
//        ProductOption prodOption = productOptionRepository.findByInventory(inventory).orElseThrow(
//                ()->new NoSuchElementException("Inventory not found"));
//        BrandMember brandMem = brandMemberRepository.findByBrand(prodOption.getProduct().getBrand()).orElseThrow(
//                ()->new NoSuchElementException("Product not found")
//        );
//        NotificationMetadata resultNotificationMetadata = notificationMetadataRepository.findByNotificationCategory("STOCKLACK").orElseThrow(()->new NoSuchElementException("Notification Metadata not found"));
//
//        Notification notification = Notification.builder()
//                .user(userRepository.findById(brandMem.getUserId()).orElseThrow(
//                        ()->new NoSuchElementException("User not found")
//                ))
//                .notificationMetadata(resultNotificationMetadata)
//                .notificationTitle(prodOption.getProduct().getProductName()+resultNotificationMetadata.getNotificationTitle())
//                .notificationMessage(resultNotificationMetadata.getNotificationMessage())
//                .notificationUrl(resultNotificationMetadata.getNotificationUrl())
//                .build();
//        notificationRepository.save(notification);
//
//        if (fcmService != null) {
//            try{
//                fcmService.sendMessageByToken(notification.getNotificationTitle(),notification.getNotificationMessage(),firebaseTokenService.readFirebaseTokens(brandMem.getUserId()));
//            }catch (FirebaseMessagingException e){
//                log.error(e.getMessage());
//            }
//        } else {
//            log.info("FCM이 비활성화되어 있습니다. 푸시 알림을 전송하지 않습니다.");
//        }
//    }

//    public void createNotificationFromDTO (NotificationDTO dto){
//        Notification note = Notification.builder()
//                .user(userRepository.findById(dto.getUserId()).orElseThrow(()->new NoSuchElementException("User not found")))
//                .notificationMetadata(notificationMetadataRepository.findByNotificationCategory("CHAT").orElseThrow(()->new NoSuchElementException("Notification Metadata not found")))
//                .notificationTitle(dto.getNotificationTitle())
//                .notificationMessage(dto.getNotificationMessage())
//                .notificationUrl(dto.getNotificationUrl())
//                .build();
//        notificationRepository.save(note);
//        if (fcmService != null) {
//            fcmService.sendMessageByToken(note.getNotificationTitle(),note.getNotificationMessage(),firebaseTokenService.readFirebaseTokens(List.of(dto.getUserId())));
//        } else {
//            log.info("FCM이 비활성화되어 있습니다. 푸시 알림을 전송하지 않습니다.");
//        }
//    }
}
