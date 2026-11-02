package com.example.resay.domain.user.controller;

import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserDailyActivityJdbcRepository;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class UserActivityRecordingTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private UserDailyActivityJdbcRepository repository;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                User.createLocal(UUID.randomUUID() + "@example.com", "encoded-password", "닉네임"));
        bearer = "Bearer " + jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @Test
    void recordsAuthenticatedRequestOnceToday() throws Exception {
        mockMvc.perform(get(ME_URL).header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get(ME_URL).header("Authorization", bearer)).andExpect(status().isOk());

        assertThat(rows(user.getId())).containsExactly(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    @Test
    void doesNotRecordUnauthenticatedRequests() throws Exception {
        long before = totalRows();

        mockMvc.perform(get(ME_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
        // 인증 없이 허용된 API는 토큰이 붙어 있어도 읽지 않으므로 활동으로 치지 않는다
        mockMvc.perform(post("/api/v1/auth/email/check")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\"}"))
                .andExpect(status().isOk());

        assertThat(totalRows()).isEqualTo(before);
    }

    @Test
    void requestSucceedsWhenRecordingFails(CapturedOutput output) throws Exception {
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(repository).insertIfAbsent(eq(user.getId()), any());

        mockMvc.perform(get(ME_URL).header("Authorization", bearer)).andExpect(status().isOk());

        assertThat(output).contains("일별 활동 기록에 실패했습니다.");
        assertThat(rows(user.getId())).isEmpty();
    }

    // 캐시가 비어 있는 재시작 직후처럼 같은 행을 동시에 넣어도 오류 없이 한 줄만 남아야 한다.
    // 실제 잠금 동작은 MySQL로 돌려야 확인된다 (CLAUDE.md "동시성이 걸린 코드")
    @Test
    void concurrentInsertsLeaveOneRow() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<Callable<Void>> tasks = IntStream.range(0, 16)
                .<Callable<Void>>mapToObj(i -> () -> {
                    repository.insertIfAbsent(user.getId(), today);
                    return null;
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (var future : pool.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(rows(user.getId())).containsExactly(today);
    }

    private List<LocalDate> rows(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT activity_date FROM user_daily_activities WHERE user_id = ?", LocalDate.class, userId);
    }

    private long totalRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_daily_activities", Long.class);
    }
}
