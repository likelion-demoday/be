package com.example.resay.domain.user.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                User.createLocal("user@example.com", "encoded-password", "닉네임"));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @Test
    void returnsMyInfo() throws Exception {
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER200_1"))
                .andExpect(jsonPath("$.result.id").value(user.getId()))
                .andExpect(jsonPath("$.result.email").value("user@example.com"))
                .andExpect(jsonPath("$.result.nickname").value("닉네임"))
                .andExpect(jsonPath("$.result.provider").value("LOCAL"))
                .andExpect(jsonPath("$.result.createdAt", notNullValue()))
                .andExpect(jsonPath("$.result.joinedDays").value(1))
                .andExpect(jsonPath("$.result.analysisCount").value(0))
                .andExpect(jsonPath("$.result.totalRecordingSeconds").value(0))
                // 비밀번호 같은 내부 정보는 내려가지 않는다
                .andExpect(jsonPath("$.result.password").doesNotExist());
    }

    @Test
    void returnsNullEmailForSocialAccountWithoutEmail() throws Exception {
        User socialUser = userRepository.saveAndFlush(
                User.createSocial(Provider.KAKAO, "1234567890", null, "카카오유저"));
        String socialToken = jwtTokenProvider
                .issueAccessToken(socialUser.getId(), socialUser.getRole().name()).value();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + socialToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email", nullValue()))
                .andExpect(jsonPath("$.result.provider").value("KAKAO"));
    }

    @Test
    void countsOnlyCompletedRecordingsUserKept() throws Exception {
        saveRecording(user.getId(), 600, RecordingStatus.COMPLETED, false);
        saveRecording(user.getId(), 300, RecordingStatus.COMPLETED, false);
        saveRecording(user.getId(), 1_000, RecordingStatus.COMPLETED, true); // 사용자가 지움
        saveRecording(user.getId(), 200, RecordingStatus.FAILED, false);
        saveRecording(user.getId(), 100, RecordingStatus.ANALYZING, false);
        saveRecording(user.getId() + 1, 999, RecordingStatus.COMPLETED, false); // 다른 사용자

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.analysisCount").value(2))
                .andExpect(jsonPath("$.result.totalRecordingSeconds").value(900));
    }

    @Test
    void countsJoinedDaysByCalendarDate() throws Exception {
        // 24시간이 아니라 날짜로 센다: 이틀 전 23:59에 가입했으면 오늘이 3일째
        jdbcTemplate.update("update users set created_at = ? where id = ?",
                LocalDate.now().minusDays(2).atTime(23, 59), user.getId());
        entityManager.clear();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.result.joinedDays").value(3));
        // 닉네임을 수정한 응답도 같은 값을 준다
        updateNickname("새닉네임")
                .andExpect(jsonPath("$.result.joinedDays").value(3))
                .andExpect(jsonPath("$.result.analysisCount").value(0));
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void updatesNickname() throws Exception {
        updateNickname("  새닉네임  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER200_2"))
                // 앞뒤 공백은 제거된다
                .andExpect(jsonPath("$.result.nickname").value("새닉네임"));

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.result.nickname").value("새닉네임"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname())
                .isEqualTo("새닉네임");
    }

    @Test
    void rejectsBlankNickname() throws Exception {
        updateNickname("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
    }

    @Test
    void rejectsTooLongNickname() throws Exception {
        updateNickname("가".repeat(21))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
    }

    @Test
    void doesNotChangeEmailOrProvider() throws Exception {
        mockMvc.perform(patch(ME_URL)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "새닉네임", "email": "hacker@example.com", "provider": "KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("user@example.com"))
                .andExpect(jsonPath("$.result.provider").value("LOCAL"));
    }

    @Test
    void returnsNotFoundWhenAccountNoLongerExists() throws Exception {
        String tokenOfDeletedUser = jwtTokenProvider.issueAccessToken(999_999L, "USER").value();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + tokenOfDeletedUser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER404_1"));
    }

    private void saveRecording(Long userId, int durationSeconds, RecordingStatus status, boolean deleted) {
        Recording recording = Recording.create(userId, "recordings/test.m4a", durationSeconds);
        ReflectionTestUtils.setField(recording, "status", status);
        if (deleted) {
            ReflectionTestUtils.setField(recording, "deletedAt", LocalDateTime.now());
        }
        recordingRepository.saveAndFlush(recording);
    }

    private ResultActions updateNickname(String nickname) throws Exception {
        return mockMvc.perform(patch(ME_URL)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "%s"}
                        """.formatted(nickname)));
    }
}
