package com.example.resay.domain.notification.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 알림 메일 발송 기록. 같은 종류 · 같은 대상(녹음 등)으로는 한 번만 보내도록 유니크 제약을 둔다.
// 받는 사람 주소와 메일 내용은 남기지 않는다
@Getter
@Entity
@Table(
        name = "notification_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_logs_type_target", columnNames = {"type", "target_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

    private static final int MAX_FAILURE_REASON_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private NotificationType type;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private NotificationLogStatus status;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON_LENGTH)
    private String failureReason;

    // 메일 서비스가 준 발송 ID. 발송 문의 때 메일 서비스 대시보드에서 찾는 데 쓴다
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    private NotificationLog(Long userId, NotificationType type, Long targetId) {
        this.userId = userId;
        this.type = type;
        this.targetId = targetId;
        this.status = NotificationLogStatus.PENDING;
    }

    public static NotificationLog pending(Long userId, NotificationType type, Long targetId) {
        return new NotificationLog(userId, type, targetId);
    }

    public void markSent(String providerMessageId) {
        this.status = NotificationLogStatus.SENT;
        this.providerMessageId = providerMessageId;
    }

    public void markNotConfigured() {
        this.status = NotificationLogStatus.NOT_CONFIGURED;
    }

    public void markFailed(String reason) {
        this.status = NotificationLogStatus.FAILED;
        this.failureReason = reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
