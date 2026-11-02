package com.example.resay.domain.user.repository;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserDailyActivityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    // 동시 요청이나 재시작 뒤의 재기록은 중복 오류 없이 넘어간다
    public void insertIfAbsent(Long userId, LocalDate activityDate) {
        jdbcTemplate.update(
                "INSERT INTO user_daily_activities (user_id, activity_date) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE user_id = user_id",
                userId, activityDate
        );
    }
}
