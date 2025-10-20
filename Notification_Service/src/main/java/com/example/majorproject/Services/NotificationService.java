package com.example.majorproject.Services;

import com.example.majorproject.Models.Notification;
import com.example.majorproject.Models.NotificationStatus;
import com.example.majorproject.Models.NotificationType;
import com.example.majorproject.Repositories.NotificationRepository;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.util.*;

@Service
public class NotificationService {


    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);


    private final String NOTIFICATION_STATUS_TOPIC="notification-status";

    JSONParser jsonParser = new JSONParser();


    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    RedisTemplate  redisTemplate;



    @KafkaListener(topics = "${kafka.topic.user-created}", groupId = "notification-service-group")
    public void userCreated(String msg) throws ParseException {
        JSONObject data;
        try{
            data=(JSONObject) jsonParser.parse(msg);
        }
        catch (ParseException e){
            logger.error("Failed to parse notification-status event: {}", msg, e);
            return;
        }
        // Safely handle number conversion
        Long userIdLong = (Long) data.get("id");
        Integer userId = userIdLong.intValue();

        Map<Object, Object> profile = redisTemplate.opsForHash().entries("USER:" + userId);

        if(profile.isEmpty()){
            logger.error("No user profile found in Redis for userId={}", userId);
                return;
        }
        String name=(String)profile.get("name");
        String message = "Welcome to WalletWave, " + name + "! 🎉";
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .type(NotificationType.USER)
                .status(NotificationStatus.UNREAD)
                .build();
        notificationRepository.save(notification);
        logger.info("Greeting notification saved for userId={}, name={}", userId, name);
    }


    @KafkaListener(topics = NOTIFICATION_STATUS_TOPIC, groupId = "wallet-updated-group")
    public void walletUpdated(String msg) {
        logger.info("Received notification-status event: {}", msg);
        JSONObject data;
        try {
            data = (JSONObject) jsonParser.parse(msg);
        } catch (ParseException e) {
            logger.error("Failed to parse notification-status event: {}", msg, e);
            return;
        }

        Integer senderId = ((Number) data.get("sender")).intValue();
        Integer receiverId = ((Number) data.get("receiver")).intValue();
        Long amount = ((Number) data.get("amount")).longValue();
        String status = data.get("status").toString().toUpperCase();
        String reason = data.containsKey("reason") ? (String) data.get("reason") : null;

        // Fetch profiles from Redis
        Map<Object, Object> senderProfile = redisTemplate.opsForHash().entries("USER:" + senderId);
        Map<Object, Object> receiverProfile = redisTemplate.opsForHash().entries("USER:" + receiverId);

        if (senderProfile == null || senderProfile.isEmpty() ||
                receiverProfile == null || receiverProfile.isEmpty()) {
            logger.warn("⚠️ Missing Redis profile for senderId={} or receiverId={}, skipping notification", senderId, receiverId);
            return;
        }

        if ("SUCCESS".equals(status)) {
            String senderName = (String) senderProfile.get("name");
            String receiverName = (String) receiverProfile.get("name");

            String senderMessage = "Your payment of $" + amount + " to " + receiverName + " was SUCCESSFUL.";
            Notification notifySender = Notification.builder()
                    .userId(senderId)
                    .message(senderMessage)
                    .type(NotificationType.TRANSACTION)
                    .status(NotificationStatus.UNREAD)
                    .build();

            String receiverMessage = "You received $" + amount + " from " + senderName + ".";
            Notification notifyReceiver = Notification.builder()
                    .userId(receiverId)
                    .message(receiverMessage)
                    .type(NotificationType.TRANSACTION)
                    .status(NotificationStatus.UNREAD)
                    .build();

            notificationRepository.saveAll(Arrays.asList(notifySender, notifyReceiver));
            logger.info("Saved 2 notifications for txnId={}", data.get("externalTransactionId"));

        }
        else if ("FAILED".equals(status)) {
            String receiverName = receiverProfile.containsKey("name")
                    ? (String) receiverProfile.get("name")
                    : "Unknown User";

            String message = "Your payment of $" + amount + " to " + receiverName + " FAILED."
                    + (reason != null ? " Reason: " + reason : "");

            Notification notification = Notification.builder()
                    .userId(senderId)
                    .message(message)
                    .type(NotificationType.TRANSACTION)
                    .status(NotificationStatus.UNREAD)
                    .build();

            notificationRepository.save(notification);
            logger.info("Saved 1 notification for txnId={}", data.get("externalTransactionId"));
        }
    }



    // ✅ Get all notifications for a user
    public List<Notification> getNotificationsByUser(Integer userId) {

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ✅ Mark single notification as READ
    public Notification markAsRead(Integer notificationId, Integer userId) throws AccessDeniedException {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("You cannot modify someone else’s notification");
        }

        notification.setStatus(NotificationStatus.READ);
        return notificationRepository.save(notification);
    }

    // ✅ Mark all notifications for a user as READ
    public int markAllAsRead(Integer userId) {
        List<Notification> notification=this.notificationRepository.findByUserId(userId);
        notification.forEach(n -> n.setStatus(NotificationStatus.READ));
        notificationRepository.saveAll(notification);
        return notification.size();
    }
}