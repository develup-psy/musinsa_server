package com.mudosa.musinsa.notification.domain.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.mudosa.musinsa.fbtoken.dto.FBTokenDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true", matchIfMissing = true)
public class FcmService {

    @Value("${fcm.service-account-file}")
    private String serviceAccountFilePath;

    @Value("${fcm.topic-name}")
    private String topicName;

    @Value("${fcm.project-id}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new ClassPathResource(serviceAccountFilePath).getInputStream()))
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("FirebaseApp has been initialized.");
            } catch (IOException e) {
                log.error("Failed to initialize FirebaseApp", e);
                throw new IllegalStateException("Failed to initialize FirebaseApp", e);
            }
        }
    }

//    public void sendMessageByTopic(String title, String body) throws IOException, FirebaseMessagingException {
//        FirebaseMessaging.getInstance().send(Message.builder()
//            .setNotification(Notification.builder()
//                .setTitle(title)
//                .setBody(body)
//                .build())
//            .setTopic(topicName)
//            .build());
//    }

    public boolean sendMessageByToken(String title,String body,List<FBTokenDTO> tokenList){
        List<String> registrationTokens = tokenList.stream()
                .map(FBTokenDTO::getToken)
                .toList();

        if(registrationTokens.isEmpty()){
            log.info("메세지를 보낼 토큰이 없습니다.");
            return false;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build())
                .addAllTokens(registrationTokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            if (response.getFailureCount() > 0){
                log.warn("FCM messages failed to send to {} devices.",response.getFailureCount());
            }
            log.info("Successfully sent FCM messages to {} devices.",response.getSuccessCount());
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("Error sending Multicast message.", e);
            return false;
        }
    }
}
