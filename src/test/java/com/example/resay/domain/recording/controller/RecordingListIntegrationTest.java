package com.example.resay.domain.recording.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecordingListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.createLocal("list@example.com", "encoded", "지현"));
    }

    @Test
    void 분석이_끝난_대화만_최신순으로_보여준다() throws Exception {
        Recording older = saveRecording(user, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 21, 0));
        Recording newer = saveRecording(user, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, 3, 21, 0));
        // 진행 중이거나 실패한 녹음, 지운 대화, 다른 사람의 대화는 보이지 않는다
        saveRecording(user, RecordingStatus.TYPE_SELECTED, LocalDateTime.of(2026, 10, 4, 21, 0));
        saveRecording(user, RecordingStatus.ANALYZING, LocalDateTime.of(2026, 10, 4, 21, 0));
        saveRecording(user, RecordingStatus.FAILED, LocalDateTime.of(2026, 10, 4, 21, 0));
        Recording deleted = saveRecording(user, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, 4, 21, 0));
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        User other = userRepository.save(User.createLocal("list-other@example.com", "encoded", "남"));
        saveRecording(other, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, 4, 21, 0));
        entityManager.flush();

        getList(0, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_6"))
                .andExpect(jsonPath("$.result.items[*].recordingId").value(contains(
                        newer.getId().intValue(), older.getId().intValue())))
                .andExpect(jsonPath("$.result.items[0].title").value(newer.getTitle()))
                .andExpect(jsonPath("$.result.items[0].relationshipType").value("FRIEND_DAILY"))
                .andExpect(jsonPath("$.result.items[0].durationSeconds").value(600))
                .andExpect(jsonPath("$.result.totalElements").value(2));
    }

    @Test
    void 보관_기간이_지나_음성이_삭제된_대화도_목록에_남고_다시_들을_수_없다고_알려준다() throws Exception {
        Recording recording = saveRecording(user, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 21, 0));
        recording.markAudioDeleted();
        entityManager.flush();

        getList(0, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.items[0].audioAvailable").value(false));
    }

    @Test
    void 페이지_단위로_나눠_보여준다() throws Exception {
        for (int day = 1; day <= 3; day++) {
            saveRecording(user, RecordingStatus.COMPLETED, LocalDateTime.of(2026, 10, day, 21, 0));
        }
        entityManager.flush();

        getList(1, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.page").value(1))
                .andExpect(jsonPath("$.result.size").value(2))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void 대화가_없으면_빈_목록() throws Exception {
        getList(0, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_400() throws Exception {
        getList(0, 0).andExpect(status().isBadRequest());
        getList(0, 51).andExpect(status().isBadRequest());
        getList(-1, 10).andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/recordings"))
                .andExpect(status().isUnauthorized());
    }

    private Recording saveRecording(User owner, RecordingStatus status, LocalDateTime createdAt) {
        Recording recording = Recording.create(owner.getId(), "/storage/" + System.nanoTime() + ".m4a", 600);
        ReflectionTestUtils.setField(recording, "relationshipType", RelationshipType.FRIEND_DAILY);
        ReflectionTestUtils.setField(recording, "status", status);
        recordingRepository.saveAndFlush(recording);
        // 생성 시각은 저장할 때 자동으로 채워지고 수정되지 않으므로 순서 확인을 위해 직접 바꾼다
        entityManager.createNativeQuery("update recordings set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", recording.getId())
                .executeUpdate();
        entityManager.refresh(recording);
        return recording;
    }

    private ResultActions getList(int page, int size) throws Exception {
        return mockMvc.perform(get("/api/v1/recordings")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .header("Authorization", "Bearer " + token(user)));
    }

    private String token(User requester) {
        return jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
    }
}
