package com.example.majorproject.Repositories;

import com.example.majorproject.Models.Notification;
import com.example.majorproject.Models.NotificationStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface NotificationRepository extends JpaRepository<Notification,Integer> {


    List<Notification> findByUserId(Integer userId);

    @Transactional
    @Modifying
    @Query("update Notification n set n.status = :status where n.userId = :userId")
    int updateNotifications(@Param("userId") Integer userId, @Param("status") NotificationStatus status);

    @Transactional
    @Modifying
    @Query("update Notification n set n.status = :status where n.id = :notificationId")
    int updateNotificationStatus(@Param("notificationId") Integer notificationId,
                                 @Param("status") NotificationStatus status);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
