package com.example.resay.domain.notification.controller;

import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingControllerTest {

    private static final String SETTINGS_URL = "/api/v1/users/me/notifications";
    private static final String EMAIL_URL = "/api/v1/users/me/notification-email";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("login@example.com", "encoded", "지현"));
        user.agreeToTerms(LocalDateTime.now(), false);
    }

    @Test
    void 저장한_적이_없으면_이메일_없음_모두_꺼짐으로_준다() throws Exception {
        getSettings(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION200_1"))
                .andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.analysis").value(false))
                .andExpect(jsonPath("$.result.service").value(false))
                .andExpect(jsonPath("$.result.marketing").value(false));
    }

    @Test
    void 가입때_마케팅에_동의했으면_마케팅은_켜짐으로_준다() throws Exception {
        user.agreeToTerms(LocalDateTime.now(), true);

        getSettings(user).andExpect(jsonPath("$.result.marketing").value(true));
    }

    @Test
    void 알림_이메일은_로그인_이메일과_별개로_저장한다() throws Exception {
        putEmail(user, "{\"email\": \"  alert@example.com \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION200_3"))
                .andExpect(jsonPath("$.result.email").value("alert@example.com"));

        getSettings(user).andExpect(jsonPath("$.result.email").value("alert@example.com"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo("login@example.com");
    }

    @Test
    void 다른_사용자와_같은_알림_이메일도_쓸_수_있다() throws Exception {
        User other = userRepository.saveAndFlush(User.createLocal("other@example.com", "encoded", "남"));
        putEmail(other, "{\"email\": \"family@example.com\"}").andExpect(status().isOk());

        putEmail(user, "{\"email\": \"family@example.com\"}").andExpect(status().isOk());
    }

    @Test
    void 이메일_형식이_틀리면_400() throws Exception {
        putEmail(user, "{\"email\": \"not-an-email\"}").andExpect(status().isBadRequest());

        getSettings(user).andExpect(jsonPath("$.result.email").doesNotExist());
    }

    @Test
    void 도메인에_점이_없는_이메일은_400() throws Exception {
        putEmail(user, "{\"email\": \"user@localhost\"}").andExpect(status().isBadRequest());

        getSettings(user).andExpect(jsonPath("$.result.email").doesNotExist());
    }

    @Test
    void 이메일이_100자를_넘으면_400() throws Exception {
        String longEmail = "a".repeat(90) + "@example.com";

        putEmail(user, "{\"email\": \"" + longEmail + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 빈_이메일을_보내면_지우고_알림을_모두_끈다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");
        patchSettings(user, "{\"analysis\": true, \"service\": true, \"marketing\": true}");

        putEmail(user, "{\"email\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.analysis").value(false))
                .andExpect(jsonPath("$.result.service").value(false))
                .andExpect(jsonPath("$.result.marketing").value(false));
        flushAndClear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().getMarketingAgreedAt()).isNull();
    }

    @Test
    void 이메일을_다시_넣어도_알림은_꺼진_채로_둔다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");
        patchSettings(user, "{\"analysis\": true}");
        putEmail(user, "{\"email\": \"\"}");

        putEmail(user, "{\"email\": \"new@example.com\"}")
                .andExpect(jsonPath("$.result.email").value("new@example.com"))
                .andExpect(jsonPath("$.result.analysis").value(false));
    }

    @Test
    void 이메일을_바꾸기만_하면_알림_설정은_유지한다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");
        patchSettings(user, "{\"analysis\": true, \"marketing\": true}");

        putEmail(user, "{\"email\": \"new@example.com\"}")
                .andExpect(jsonPath("$.result.analysis").value(true))
                .andExpect(jsonPath("$.result.marketing").value(true));
    }

    @Test
    void 이메일_없이_알림을_켜면_400이고_아무것도_바뀌지_않는다() throws Exception {
        for (String body : new String[]{"{\"analysis\": true}", "{\"service\": true}", "{\"marketing\": true}"}) {
            patchSettings(user, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION400_1"));
        }

        getSettings(user)
                .andExpect(jsonPath("$.result.analysis").value(false))
                .andExpect(jsonPath("$.result.service").value(false))
                .andExpect(jsonPath("$.result.marketing").value(false));
    }

    @Test
    void 이메일이_없어도_알림을_끌_수는_있다() throws Exception {
        user.agreeToTerms(LocalDateTime.now(), true);

        patchSettings(user, "{\"marketing\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marketing").value(false));
    }

    @Test
    void 보낸_항목만_바꾼다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");

        patchSettings(user, "{\"analysis\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION200_2"))
                .andExpect(jsonPath("$.result.analysis").value(true))
                .andExpect(jsonPath("$.result.service").value(false))
                .andExpect(jsonPath("$.result.marketing").value(false));

        patchSettings(user, "{\"service\": true}")
                .andExpect(jsonPath("$.result.analysis").value(true))
                .andExpect(jsonPath("$.result.service").value(true));
    }

    @Test
    void 마케팅_알림은_약관의_마케팅_수신_동의와_같은_값이다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");

        patchSettings(user, "{\"marketing\": true}").andExpect(jsonPath("$.result.marketing").value(true));
        flushAndClear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().getMarketingAgreedAt()).isNotNull();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.result.marketingAgreed").value(true));
    }

    @Test
    void 설정은_저장된다() throws Exception {
        putEmail(user, "{\"email\": \"alert@example.com\"}");
        patchSettings(user, "{\"analysis\": true, \"service\": true}");
        flushAndClear();

        getSettings(user)
                .andExpect(jsonPath("$.result.email").value("alert@example.com"))
                .andExpect(jsonPath("$.result.analysis").value(true))
                .andExpect(jsonPath("$.result.service").value(true));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get(SETTINGS_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(EMAIL_URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"a@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions getSettings(User requester) throws Exception {
        return mockMvc.perform(get(SETTINGS_URL).header("Authorization", bearer(requester)));
    }

    private ResultActions patchSettings(User requester, String body) throws Exception {
        return mockMvc.perform(patch(SETTINGS_URL)
                .header("Authorization", bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putEmail(User requester, String body) throws Exception {
        return mockMvc.perform(put(EMAIL_URL)
                .header("Authorization", bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String bearer(User requester) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
    }
}
