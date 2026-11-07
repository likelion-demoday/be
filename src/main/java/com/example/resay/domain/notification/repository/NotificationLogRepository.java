package com.example.resay.domain.notification.repository;

import com.example.resay.domain.notification.entity.NotificationLog;
import com.example.resay.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByTypeAndTargetId(NotificationType type, Long targetId);

    Optional<NotificationLog> findByTypeAndTargetId(NotificationType type, Long targetId);
}
