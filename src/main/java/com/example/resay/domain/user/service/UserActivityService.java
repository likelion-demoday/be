package com.example.resay.domain.user.service;

import com.example.resay.domain.user.repository.UserDailyActivityJdbcRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserActivityService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserDailyActivityJdbcRepository repository;
    private final Clock clock;
    // 모든 요청이 지나는 경로라 사용자당 하루 한 번만 DB에 쓴다. 사용자 수만큼만 커지고, 재시작으로 비어도 중복 없이 다시 채워진다
    private final Map<Long, LocalDate> lastRecordedDates = new ConcurrentHashMap<>();

    @Autowired
    public UserActivityService(UserDailyActivityJdbcRepository repository) {
        this(repository, Clock.system(SEOUL));
    }

    UserActivityService(UserDailyActivityJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(Long userId) {
        LocalDate today = LocalDate.now(clock);
        if (today.equals(lastRecordedDates.get(userId))) {
            return;
        }
        repository.insertIfAbsent(userId, today);
        // 저장에 실패하면 남기지 않아서 다음 요청이 다시 시도한다
        lastRecordedDates.put(userId, today);
    }
}
