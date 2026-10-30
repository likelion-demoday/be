package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreditControllerTest {

    private static final String SUMMARY_URL = "/api/v1/credits/me";
    private static final String HISTORY_URL = "/api/v1/credits/history";
    private static final String PRICES_URL = "/api/v1/credits/prices";
    private static final Long ADMIN_ID = 999_999L;
    private static final Long PAYMENT_ID = 777L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CreditService creditService;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("credit-api@example.com", "encoded-password", "닉네임"));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get(SUMMARY_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
        mockMvc.perform(get(HISTORY_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PRICES_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsZeroBalanceForNewUser() throws Exception {
        mockMvc.perform(get(SUMMARY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CREDIT200_1"))
                .andExpect(jsonPath("$.result.balance").value(0))
                .andExpect(jsonPath("$.result.characterFreeAvailable").value(true));
    }

    @Test
    void returnsBalanceAndFreeChanceAfterUse() throws Exception {
        creditService.adjust(user.getId(), 3000, "테스트 지급", ADMIN_ID);
        creditService.useForAnalysis(user.getId(), 1L, AnalysisCategory.DAILY);
        creditService.useForCharacter(user.getId(), 1L);

        mockMvc.perform(get(SUMMARY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.balance").value(1500))
                .andExpect(jsonPath("$.result.characterFreeAvailable").value(false));
    }

    @Test
    void returnsHistoryNewestFirst() throws Exception {
        creditService.adjust(user.getId(), 3000, "운영자만 보는 사유", ADMIN_ID);
        creditService.useForAnalysis(user.getId(), 11L, AnalysisCategory.CONFLICT);
        creditService.refund(UsagePurpose.ANALYSIS, 11L, "전사 실패");

        mockMvc.perform(get(HISTORY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CREDIT200_2"))
                .andExpect(jsonPath("$.result.items", hasSize(3)))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items[0].type").value("USE_REFUND"))
                .andExpect(jsonPath("$.result.items[0].amount").value(2000))
                .andExpect(jsonPath("$.result.items[0].balanceAfter").value(3000))
                .andExpect(jsonPath("$.result.items[0].purpose").value("ANALYSIS"))
                .andExpect(jsonPath("$.result.items[0].recordingId").value(11))
                .andExpect(jsonPath("$.result.items[0].createdAt", notNullValue()))
                .andExpect(jsonPath("$.result.items[1].type").value("USE"))
                .andExpect(jsonPath("$.result.items[1].amount").value(-2000))
                .andExpect(jsonPath("$.result.items[1].balanceAfter").value(1000))
                .andExpect(jsonPath("$.result.items[2].type").value("ADJUST"))
                .andExpect(jsonPath("$.result.items[2].amount").value(3000))
                .andExpect(jsonPath("$.result.items[2].purpose", nullValue()))
                // 운영자 조정 사유와 처리자는 사용자에게 내려가지 않는다
                .andExpect(jsonPath("$.result.items[2].memo").doesNotExist())
                .andExpect(jsonPath("$.result.items[2].createdBy").doesNotExist());
    }

    // 충전은 내역에 CHARGE로 나온다. 사용 건이 아니므로 용도 · 녹음은 비어 있다
    @Test
    void showsChargeInHistory() throws Exception {
        creditService.charge(user.getId(), PAYMENT_ID, 3000);
        creditService.useForAnalysis(user.getId(), 21L, AnalysisCategory.DAILY);

        mockMvc.perform(get(HISTORY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(2)))
                .andExpect(jsonPath("$.result.items[0].type").value("USE"))
                .andExpect(jsonPath("$.result.items[0].balanceAfter").value(1500))
                .andExpect(jsonPath("$.result.items[1].type").value("CHARGE"))
                .andExpect(jsonPath("$.result.items[1].amount").value(3000))
                .andExpect(jsonPath("$.result.items[1].balanceAfter").value(3000))
                .andExpect(jsonPath("$.result.items[1].purpose", nullValue()))
                .andExpect(jsonPath("$.result.items[1].recordingId", nullValue()))
                // 내부 식별자는 내려가지 않는다
                .andExpect(jsonPath("$.result.items[1].paymentId").doesNotExist())
                .andExpect(jsonPath("$.result.items[1].uniqueKey").doesNotExist());
        mockMvc.perform(get(SUMMARY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.result.balance").value(1500));
    }

    @Test
    void pagesHistory() throws Exception {
        for (int i = 0; i < 5; i++) {
            creditService.adjust(user.getId(), 100, "지급 " + i, ADMIN_ID);
        }

        mockMvc.perform(get(HISTORY_URL).param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(2)))
                .andExpect(jsonPath("$.result.items[0].balanceAfter").value(500))
                .andExpect(jsonPath("$.result.hasNext").value(true));
        mockMvc.perform(get(HISTORY_URL).param("page", "2").param("size", "2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void limitsPageSizeAndIgnoresNegativePage() throws Exception {
        for (int i = 0; i < 3; i++) {
            creditService.adjust(user.getId(), 100, "지급 " + i, ADMIN_ID);
        }

        mockMvc.perform(get(HISTORY_URL).param("page", "-1").param("size", "100000")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(50))
                .andExpect(jsonPath("$.result.items", hasSize(3)));
        mockMvc.perform(get(HISTORY_URL).param("size", "0")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(1));
    }

    @Test
    void showsOnlyMyHistory() throws Exception {
        User other = userRepository.saveAndFlush(User.createLocal("other@example.com", "encoded-password", "다른사람"));
        creditService.adjust(other.getId(), 9000, "다른 사람 지급", ADMIN_ID);

        mockMvc.perform(get(HISTORY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(0)));
        mockMvc.perform(get(SUMMARY_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.result.balance").value(0));
    }

    @Test
    void returnsPrices() throws Exception {
        mockMvc.perform(get(PRICES_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CREDIT200_3"))
                .andExpect(jsonPath("$.result.analysis.daily").value(1500))
                .andExpect(jsonPath("$.result.analysis.conflict").value(2000))
                .andExpect(jsonPath("$.result.character").value(500));
    }
}
