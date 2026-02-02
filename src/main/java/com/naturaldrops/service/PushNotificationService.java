package com.naturaldrops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturaldrops.entity.User;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {
    
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${expo.push.api.url:https://exp.host/--/api/v2/push/send}")
    private String expoPushApiUrl;
    
    /**
     * Send push notification to a buyer
     * @param buyerId The ID of the buyer
     * @param title Notification title
     * @param body Notification body/message
     * @param data Additional data to send with notification
     */
    public void sendNotificationToBuyer(Long buyerId, String title, String body, Map<String, Object> data) {
        try {
            User buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new RuntimeException("Buyer not found with id: " + buyerId));
            
            String deviceToken = buyer.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.warn("Buyer {} does not have a device token registered", buyerId);
                return;
            }
            
            sendExpoPushNotification(deviceToken, title, body, data);
        } catch (Exception e) {
            log.error("Error sending push notification to buyer {}: {}", buyerId, e.getMessage(), e);
        }
    }
    
    /**
     * Send push notification using Expo Push Notification service
     * This works with Expo apps and React Native apps using Expo
     */
    private void sendExpoPushNotification(String deviceToken, String title, String body, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("to", deviceToken);
            notification.put("title", title);
            notification.put("body", body);
            notification.put("sound", "default");
            notification.put("priority", "high");
            notification.put("channelId", "default");
            
            if (data != null && !data.isEmpty()) {
                notification.put("data", data);
            }
            
            List<Map<String, Object>> messages = Collections.singletonList(notification);
            
            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(messages),
                    headers
            );
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                    expoPushApiUrl,
                    request,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Push notification sent successfully to device: {}", deviceToken);
            } else {
                log.error("Failed to send push notification. Status: {}, Response: {}", 
                        response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Error sending Expo push notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send FCM notification (for native apps using FCM directly)
     * This requires Firebase Admin SDK configuration
     */
    public void sendFCMNotification(String deviceToken, String title, String body, Map<String, Object> data) {
        // TODO: Implement FCM using Firebase Admin SDK if needed
        // This would require Firebase service account credentials
        log.warn("FCM notification not implemented. Using Expo push notification service instead.");
        sendExpoPushNotification(deviceToken, title, body, data);
    }
}

