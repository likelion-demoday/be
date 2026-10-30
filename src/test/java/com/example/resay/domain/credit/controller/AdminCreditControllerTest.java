package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.user.entity.Role;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCreditControllerTest {

    private static final String ADJUST_URL = "/api/v1/admin/credits/adjust";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    private User target;
    private Long adminId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        target = userRepository.saveAndFlush(User.createLocal("target@example.com", "encoded-password", "대상"));
        User admin = userRepository.saveAndFlush(User.createLocal("admin@example.com", "encoded-password", "운영자"));
        adminId = admin.getId();
        adminToken = jwtTokenProvider.issueAccessToken(adminId, Role.ADMIN.name()).value();
        userToken = jwtTokenProvider.issueAccessToken(target.getId(), Role.USER.name()).value();
    }

    @Test
    void rejectsRequestWithoutLogin() throws Exception {
        mockMvc.perform(post(ADJUST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(target.getId(), 1000, "지급")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
        assertThat(balanceOf(target)).isZero();
    }

    // 일반 사용자가 자기 자신에게 크레딧을 줄 수 없어야 한다
    @Test
    void rejectsNonAdminUser() throws Exception {
        adjust(userToken, target.getId(), 1000, "셀프 지급")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403_1"));
        assertThat(balanceOf(target)).isZero();
    }

    @Test
    void adminAddsCredits() throws Exception {
        adjust(adminToken, target.getId(), 3000, "  테스트 지급  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CREDIT200_4"))
                .andExpect(jsonPath("$.result.userId").value(target.getId()))
                .andExpect(jsonPath("$.result.balance").value(3000));

        assertThat(balanceOf(target)).isEqualTo(3000);
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(target.getId()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getMemo()).isEqualTo("테스트 지급");
                    assertThat(entry.getCreatedBy()).isEqualTo(adminId);
                });
    }

    @Test
    void adminSubtractsCredits() throws Exception {
        adjust(adminToken, target.getId(), 3000, "지급").andExpect(status().isOk());

        adjust(adminToken, target.getId(), -1200, "수동 환불 처리")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.balance").value(1800));
    }

    @Test
    void rejectsSubtractingMoreThanBalance() throws Exception {
        adjust(adminToken, target.getId(), 1000, "지급").andExpect(status().isOk());

        adjust(adminToken, target.getId(), -1001, "회수")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDIT400_1"))
                .andExpect(jsonPath("$.error.balance").value(1000));
        assertThat(balanceOf(target)).isEqualTo(1000);
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        adjust(adminToken, target.getId(), 0, "의미 없음")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDIT400_1"));
        adjust(adminToken, target.getId(), 100_001, "너무 큼")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.error.amount").exists());
        adjust(adminToken, target.getId(), 1000, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.memo").exists());
        mockMvc.perform(post(ADJUST_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"memo\":\"대상 없음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.userId").exists());
        assertThat(balanceOf(target)).isZero();
    }

    @Test
    void rejectsUnknownUser() throws Exception {
        adjust(adminToken, 987_654_321L, 1000, "없는 사용자")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER404_1"));
    }

    private ResultActions adjust(String token, Long userId, int amount, String memo) throws Exception {
        return mockMvc.perform(post(ADJUST_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(userId, amount, memo)));
    }

    private String body(Long userId, int amount, String memo) {
        return "{\"userId\":%d,\"amount\":%d,\"memo\":\"%s\"}".formatted(userId, amount, memo);
    }

    private int balanceOf(User user) {
        return creditQueryService.getSummary(user.getId()).balance();
    }
}
