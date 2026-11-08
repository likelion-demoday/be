package com.example.resay.domain.notification.repository;

import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUserId(Long userId);

    // 지운 대화도 그 날짜에 서비스를 쓴 것이므로 녹음 시각에 포함한다
    @Query("""
            select new com.example.resay.domain.notification.repository.ReminderCandidate(
                u.id, u.nickname, s.email, u.createdAt,
                (select max(r.createdAt) from Recording r where r.userId = u.id),
                (select max(l.createdAt) from NotificationLog l
                  where l.userId = u.id and l.type = :type and l.status <> :failed)
            )
            from NotificationSetting s, User u
            where u.id = s.userId
              and s.email is not null
              and u.marketingAgreedAt is not null
            """)
    List<ReminderCandidate> findReminderCandidates(
            @Param("type") NotificationType type,
            @Param("failed") NotificationLogStatus failed
    );

    // 서비스 알림을 켜고 알림 이메일이 있는 사용자
    @Query("""
            select new com.example.resay.domain.notification.repository.ServiceAnnouncementTarget(s.userId, s.email)
            from NotificationSetting s
            where s.serviceEnabled = true
              and s.email is not null
              and exists (select 1 from User u where u.id = s.userId)
            order by s.userId
            """)
    List<ServiceAnnouncementTarget> findServiceAnnouncementTargets();
}
