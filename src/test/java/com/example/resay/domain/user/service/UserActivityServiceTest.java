package com.example.resay.domain.user.service;

import com.example.resay.domain.user.repository.UserDailyActivityJdbcRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class UserActivityServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserDailyActivityJdbcRepository repository = mock(UserDailyActivityJdbcRepository.class);

    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(String instant) {
            this.now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void writesOncePerUserPerDay() {
        UserActivityService service = new UserActivityService(repository, new MovableClock("2026-11-02T01:00:00Z"));

        service.record(1L);
        service.record(1L);
        service.record(2L);

        verify(repository).insertIfAbsent(1L, LocalDate.of(2026, 11, 2));
        verify(repository).insertIfAbsent(2L, LocalDate.of(2026, 11, 2));
        verifyNoMoreInteractions(repository);
    }

    // 서버 시간대가 UTC여도 날짜는 한국 자정에 바뀌어야 한다
    @Test
    void dayChangesAtSeoulMidnight() {
        MovableClock clock = new MovableClock("2026-11-02T14:59:59Z");
        UserActivityService service = new UserActivityService(repository, clock);

        service.record(1L);
        clock.now = Instant.parse("2026-11-02T15:00:00Z");
        service.record(1L);

        verify(repository).insertIfAbsent(1L, LocalDate.of(2026, 11, 2));
        verify(repository).insertIfAbsent(1L, LocalDate.of(2026, 11, 3));
    }

    @Test
    void retriesOnNextRequestWhenWriteFails() {
        UserActivityService service = new UserActivityService(repository, new MovableClock("2026-11-02T01:00:00Z"));
        doThrow(new DataAccessResourceFailureException("db down"))
                .doNothing()
                .when(repository).insertIfAbsent(anyLong(), any());

        assertThatThrownBy(() -> service.record(1L)).isInstanceOf(DataAccessResourceFailureException.class);
        service.record(1L);
        service.record(1L);

        verify(repository, times(2)).insertIfAbsent(1L, LocalDate.of(2026, 11, 2));
    }
}
