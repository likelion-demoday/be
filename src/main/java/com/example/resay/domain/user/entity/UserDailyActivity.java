package com.example.resay.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// DAU 집계용. 사용자가 그날(KST) 로그인 상태로 API를 쓰면 한 줄이 남는다. 저장은 UserDailyActivityJdbcRepository가 한다
@Getter
@Entity
@Table(
        name = "user_daily_activities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_daily_activities_user_date",
                columnNames = {"user_id", "activity_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;
}
