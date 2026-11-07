package com.example.resay.domain.notification.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자별 알림 이메일과 알림 설정. 처음 저장할 때 만들고, 없으면 이메일 없음 · 모두 꺼짐으로 본다.
// 마케팅 수신은 약관 동의 기록(users.marketing_agreed_at)을 그대로 쓴다
@Getter
@Entity
@Table(
        name = "notification_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_settings_user_id", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 로그인 이메일(users.email)과 별개로 받는 알림 수신 주소
    @Column(length = 100)
    private String email;

    @Column(name = "analysis_enabled", nullable = false)
    private boolean analysisEnabled;

    @Column(name = "service_enabled", nullable = false)
    private boolean serviceEnabled;

    private NotificationSetting(Long userId) {
        this.userId = userId;
    }

    public static NotificationSetting createDefault(Long userId) {
        return new NotificationSetting(userId);
    }

    public boolean hasEmail() {
        return this.email != null;
    }

    // 이메일을 지우면 서비스에서 알림을 모두 끈다
    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeAnalysisEnabled(boolean enabled) {
        this.analysisEnabled = enabled;
    }

    public void changeServiceEnabled(boolean enabled) {
        this.serviceEnabled = enabled;
    }
}
