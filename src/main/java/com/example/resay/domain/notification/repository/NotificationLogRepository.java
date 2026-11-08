package com.example.resay.domain.notification.repository;

import com.example.resay.domain.notification.entity.NotificationLog;
import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByTypeAndTargetId(NotificationType type, Long targetId);

    Optional<NotificationLog> findByTypeAndTargetId(NotificationType type, Long targetId);

    // 오늘 메일 서비스에 보낸(또는 보내는 중인) 통수. 거절된 발송은 한도에 들어가지 않는다
    long countByCreatedAtGreaterThanEqualAndStatusNot(LocalDateTime from, NotificationLogStatus status);
}
