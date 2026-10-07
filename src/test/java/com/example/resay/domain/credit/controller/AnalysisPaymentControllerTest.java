package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 테스트 트랜잭션은 롤백되므로 결제 커밋 뒤에 시작되는 전사는 실행되지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisPaymentControllerTest {

    private static final Long ADMIN_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("pay@example.com", "encoded-password", "결제"));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @ParameterizedTest
    @CsvSource({
            "FRIEND_DAILY, 1500",
            "COUPLE_DAILY, 1500",
            "COUPLE_CONFLICT, 2000",
            "PARENT_CHILD_CONFLICT, 2000"
    })
    void deductsCreditsByConversationTypeAndCompletesPayment(RelationshipType type, int price) throws Exception {
        creditService.adjust(user.getId(), 5000, "테스트 지급", ADMIN_ID);
        Long recordingId = recordingWithType(user.getId(), type);

        pay(accessToken, recordingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CREDIT200_5"))
                .andExpect(jsonPath("$.result.recordingId").value(recordingId))
                .andExpect(jsonPath("$.result.usedCredits").value(price))
                .andExpect(jsonPath("$.result.balance").value(5000 - price))
                .andExpect(jsonPath("$.result.alreadyPaid").value(false));

        assertThat(statusOf(recordingId)).isEqualTo(RecordingStatus.PAYMENT_COMPLETED);
        assertThat(balance()).isEqualTo(5000 - price);
    }

    @Test
    void rejectsWhenBalanceIsInsufficientAndKeepsRecordingUnpaid() throws Exception {
        creditService.adjust(user.getId(), 1000, "테스트 지급", ADMIN_ID);
        Long recordingId = recordingWithType(user.getId(), RelationshipType.FRIEND_DAILY);

        pay(accessToken, recordingId)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CREDIT402_1"))
                .andExpect(jsonPath("$.error.required").value(1500))
                .andExpect(jsonPath("$.error.balance").value(1000))
                .andExpect(jsonPath("$.error.shortage").value(500));

        assertThat(statusOf(recordingId)).isEqualTo(RecordingStatus.TYPE_SELECTED);
        assertThat(balance()).isEqualTo(1000);
    }

    // 결제 버튼을 연달아 눌러도 한 번만 차감되고, 두 번째 요청도 오류가 아니라 성공으로 응답한다
    @Test
    void doesNotChargeTwiceForSameRecording() throws Exception {
        creditService.adjust(user.getId(), 5000, "테스트 지급", ADMIN_ID);
        Long recordingId = recordingWithType(user.getId(), RelationshipType.COUPLE_CONFLICT);
        pay(accessToken, recordingId).andExpect(status().isOk());

        pay(accessToken, recordingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.usedCredits").value(2000))
                .andExpect(jsonPath("$.result.balance").value(3000))
                .andExpect(jsonPath("$.result.alreadyPaid").value(true));

        assertThat(balance()).isEqualTo(3000);
    }

    @Test
    void rejectsRecordingWithoutSelectedType() throws Exception {
        creditService.adjust(user.getId(), 5000, "테스트 지급", ADMIN_ID);
        Long recordingId = recordingRepository.saveAndFlush(
                Recording.create(user.getId(), "pay-no-type.m4a", 600)).getId();

        pay(accessToken, recordingId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORDING400_2"));

        assertThat(balance()).isEqualTo(5000);
    }

    // 실패한 녹음은 환급된 상태다. 같은 녹음으로 다시 결제할 수 없고 새로 올려야 한다
    @Test
    void rejectsFailedRecording() throws Exception {
        creditService.adjust(user.getId(), 5000, "테스트 지급", ADMIN_ID);
        Long recordingId = recordingWithType(user.getId(), RelationshipType.FRIEND_DAILY);
        Recording recording = recordingRepository.findById(recordingId).orElseThrow();
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recording.fail(RecordingFailureReason.TRANSCRIPTION_FAILED);
        recordingRepository.saveAndFlush(recording);

        pay(accessToken, recordingId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORDING400_2"));

        assertThat(balance()).isEqualTo(5000);
    }

    // 남의 녹음은 있는지조차 알려주지 않는다
    @Test
    void rejectsRecordingOfAnotherUser() throws Exception {
        creditService.adjust(user.getId(), 5000, "테스트 지급", ADMIN_ID);
        User other = userRepository.saveAndFlush(User.createLocal("owner@example.com", "encoded-password", "주인"));
        Long recordingId = recordingWithType(other.getId(), RelationshipType.FRIEND_DAILY);

        pay(accessToken, recordingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));

        assertThat(statusOf(recordingId)).isEqualTo(RecordingStatus.TYPE_SELECTED);
        assertThat(balance()).isEqualTo(5000);
    }

    @Test
    void rejectsUnknownRecording() throws Exception {
        pay(accessToken, 987_654_321L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void requiresLogin() throws Exception {
        Long recordingId = recordingWithType(user.getId(), RelationshipType.FRIEND_DAILY);

        mockMvc.perform(post("/api/v1/recordings/{id}/payment", recordingId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));

        assertThat(statusOf(recordingId)).isEqualTo(RecordingStatus.TYPE_SELECTED);
    }

    private ResultActions pay(String token, Long recordingId) throws Exception {
        return mockMvc.perform(post("/api/v1/recordings/{id}/payment", recordingId)
                .header("Authorization", "Bearer " + token));
    }

    private Long recordingWithType(Long userId, RelationshipType type) {
        Long recordingId = recordingRepository.saveAndFlush(
                Recording.create(userId, "pay-" + type + ".m4a", 600)).getId();
        recordingService.selectType(recordingId, userId, type);
        recordingRepository.flush();
        return recordingId;
    }

    private RecordingStatus statusOf(Long recordingId) {
        return recordingRepository.findById(recordingId).orElseThrow().getStatus();
    }

    private int balance() {
        return creditQueryService.getSummary(user.getId()).balance();
    }
}
