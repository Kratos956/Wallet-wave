package com.example.majorproject.Controllers;


import com.example.majorproject.Models.Notification;
import com.example.majorproject.Services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notification")
public class NotificationController {


    @Autowired
    private NotificationService notificationService;

    @Operation(summary = "Get all notifications for a user")
    @GetMapping("/me")
    public ResponseEntity<List<Notification>> getUserNotifications(Authentication authentication) {
        Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
        Integer userId = (Integer) principal.get("userId");
        return ResponseEntity.ok(notificationService.getNotificationsByUser(userId));
    }


    @Operation(summary = "Mark a single notification as read")
    @PatchMapping("/{notificationId}/mark-read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Integer notificationId,
                                                   Authentication authentication) throws AccessDeniedException {
        @SuppressWarnings("unchecked")
        Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
        Integer userId = (Integer) principal.get("userId");

        return ResponseEntity.ok(notificationService.markAsRead(notificationId, userId));
    }

    @Operation(summary = "Mark all notifications for a user as read")
    @PatchMapping("/me/mark-read")
    public ResponseEntity<String> markAllAsRead(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
        Integer userId = (Integer) principal.get("userId");

        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(updated + " notification(s) marked as READ");
    }
}
