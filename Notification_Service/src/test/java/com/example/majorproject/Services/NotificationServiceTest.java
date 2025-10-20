package com.example.majorproject.Services;


import com.example.majorproject.Models.Notification;
import com.example.majorproject.Models.NotificationStatus;
import com.example.majorproject.Models.NotificationType;
import com.example.majorproject.Repositories.NotificationRepository;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.file.AccessDeniedException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private HashOperations hashOperations;


    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }


    @Test
    public void userCreatedSuccessfully() throws ParseException {
        String msg = "{ \"id\": 1 }";

        // Mock Redis user profile
        Map<Object, Object> profile = new HashMap<>();
        profile.put("name", "Alice");
        when(hashOperations.entries("USER:1")).thenReturn(profile);

        when(notificationRepository.save(any(Notification.class))).thenReturn(null);



        notificationService.userCreated(msg);

        verify(notificationRepository, times(1)).save(any(Notification.class));



    }

    @Test
    public void userProfileIsEmpty() throws ParseException {
        String msg = "{ \"id\": 1 }";


        Map<Object, Object> profile = new HashMap<>();
        when(hashOperations.entries("USER:1")).thenReturn(profile);



        notificationService.userCreated(msg);

        verify(notificationRepository, times(0)).save(any(Notification.class));

    }

    @Test
    public void dataParsingException() throws ParseException {
        String msg = "Invalid-json";


        notificationService.userCreated(msg);

        verify(notificationRepository, times(0)).save(any(Notification.class));
    }


    @Test
    public void walletUpdatedSuccessfully() throws ParseException {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 50, " +
                "\"status\": \"SUCCESS\", " +
                "\"externalTransactionId\": \"txn-123\" }";

        Map<Object, Object> senderProfile = new HashMap<>();
        senderProfile.put("name", "Alice");
        Map<Object, Object> receiverProfile = new HashMap<>();
        receiverProfile.put("name", "Bob");

        when(hashOperations.entries("USER:1")).thenReturn(senderProfile);
        when(hashOperations.entries("USER:2")).thenReturn(receiverProfile);

        notificationService.walletUpdated(msg);

        verify(notificationRepository, times(1)).saveAll(anyList());
    }

    @Test
    public void walletUpdatedFailed() throws ParseException {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 50, " +
                "\"status\": \"FAILED\", " +
                "\"reason\": \"Insufficient balance\", " +
                "\"externalTransactionId\": \"txn-456\" }";

        Map<Object, Object> senderProfile = new HashMap<>();
        senderProfile.put("name", "Alice");
        Map<Object, Object> receiverProfile = new HashMap<>();
        receiverProfile.put("name", "Bob");

        when(hashOperations.entries("USER:1")).thenReturn(senderProfile);
        when(hashOperations.entries("USER:2")).thenReturn(receiverProfile);

        notificationService.walletUpdated(msg);

        verify(notificationRepository, times(1)).save(any(Notification.class));


    }

    @Test
    public void missingRedisProfile() throws ParseException {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 50, " +
                "\"status\": \"SUCCESS\", " +
                "\"externalTransactionId\": \"txn-123\" }";



        when(hashOperations.entries("USER:1")).thenReturn(Collections.emptyMap());
        when(hashOperations.entries("USER:2")).thenReturn(Collections.emptyMap());

        notificationService.walletUpdated(msg);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());


    }

    @Test
    public void testWalletUpdated_InvalidJson() {
        String msg = "not-a-json";

        // Act
        notificationService.walletUpdated(msg);

        // Assert: repo never called
        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }


    @Test
    public void testGetNotificationsByUser()  {
        Integer userId = 1;

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(any(Integer.class))).thenReturn(null);

        notificationService.getNotificationsByUser(userId);

        verify(notificationRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    public void testMarkAsRead() throws AccessDeniedException {
        Notification notification = Notification.builder()
                .id(1)
                .userId(10)
                .status(NotificationStatus.UNREAD)
                .message("Test message")
                .type(NotificationType.USER)
                .build();

        when(notificationRepository.findById(1)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification updated = notificationService.markAsRead(1, 10);


        Assert.assertEquals(updated.getStatus(), NotificationStatus.READ);
        verify(notificationRepository, times(1)).findById(1);
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test(expected = NoSuchElementException.class)
    public void testMarkAsRead_NotFound() throws Exception {
        when(notificationRepository.findById(1)).thenReturn(Optional.empty());

        notificationService.markAsRead(1, 10);
    }

    @Test(expected = AccessDeniedException.class)
    public void testMarkAsRead_AccessDenied() throws Exception {
        Notification notification = Notification.builder()
                .id(1)
                .userId(99) // different user
                .status(NotificationStatus.UNREAD)
                .message("Test message")
                .type(NotificationType.USER)
                .build();

        when(notificationRepository.findById(1)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1, 10); // current user != notification.userId
    }

    @Test
    public void testMarkAllAsRead_WithNotifications() {
        Integer userId = 10;

        Notification n1 = Notification.builder()
                .id(1).userId(userId).status(NotificationStatus.UNREAD).message("Msg1").build();
        Notification n2 = Notification.builder()
                .id(2).userId(userId).status(NotificationStatus.UNREAD).message("Msg2").build();

        List<Notification> notifications = Arrays.asList(n1, n2);

        when(notificationRepository.findByUserId(userId)).thenReturn(notifications);

        int updatedCount = notificationService.markAllAsRead(userId);

        // Assert

        Assert.assertEquals(2, updatedCount);
        Assert.assertEquals(NotificationStatus.READ, n1.getStatus());
        Assert.assertEquals(NotificationStatus.READ, n2.getStatus());

        verify(notificationRepository, times(1)).saveAll(notifications);
    }

    @Test
    public void testMarkAllAsRead_NoNotifications() {
        Integer userId = 20;

        when(notificationRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        int updatedCount = notificationService.markAllAsRead(userId);

        // Assert
        Assert.assertEquals(0, updatedCount);
        verify(notificationRepository, times(1)).saveAll(Collections.emptyList());
    }

}
