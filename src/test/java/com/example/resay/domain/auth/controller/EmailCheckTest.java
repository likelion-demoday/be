package com.example.resay.domain.auth.controller;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailCheckTest {

    private static final String EMAIL_CHECK_URL = "/api/v1/auth/email/check";
    private static final String SIGNUP_URL = "/api/v1/auth/signup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsAvailableForUnusedEmail() throws Exception {
        checkEmail("new@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("AUTH200_5"))
                .andExpect(jsonPath("$.result.available").value(true));
    }

    @Test
    void returnsUnavailableForRegisteredEmail() throws Exception {
        userRepository.saveAndFlush(User.createLocal("taken@example.com", "encoded-password", "닉네임"));

        checkEmail("taken@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(false));
    }

    // 가입(signup)이 대소문자 · 앞뒤 공백을 무시하므로 확인 결과도 같아야 한다
    @Test
    void ignoresCaseAndSurroundingWhitespace() throws Exception {
        userRepository.saveAndFlush(User.createLocal("taken@example.com", "encoded-password", "닉네임"));

        checkEmail("  Taken@Example.COM ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(false));
    }

    // 소셜 계정이 쓰는 이메일로는 이메일 가입이 거절되므로 미리 사용 불가로 알려준다
    @Test
    void returnsUnavailableForEmailUsedBySocialAccount() throws Exception {
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, "1234567890", "social@example.com", "카카오유저"));

        checkEmail("social@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(false));
    }

    // 확인 결과와 실제 가입 결과가 어긋나지 않아야 한다
    @Test
    void resultMatchesSignupOutcome() throws Exception {
        checkEmail("flow@example.com").andExpect(jsonPath("$.result.available").value(true));
        signup("flow@example.com").andExpect(status().isCreated());

        checkEmail("flow@example.com").andExpect(jsonPath("$.result.available").value(false));
        signup("flow@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH409_1"));
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        checkEmail("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.error.email").exists());
        checkEmail("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.email").exists());
        checkEmail("a".repeat(95) + "@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.email").exists());
        mockMvc.perform(post(EMAIL_CHECK_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 로그인 전 화면에서 쓰는 API라 토큰 없이 호출할 수 있고, 만료된 토큰이 붙어 와도 막히지 않는다
    @Test
    void worksWithoutLoginAndIgnoresBrokenToken() throws Exception {
        mockMvc.perform(post(EMAIL_CHECK_URL)
                        .header("Authorization", "Bearer broken.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(true));
    }

    // 이메일이 주소와 접근 로그에 남지 않도록 조회 방식(GET)은 받지 않는다
    @Test
    void doesNotAcceptGet() throws Exception {
        mockMvc.perform(get(EMAIL_CHECK_URL).param("email", "new@example.com"))
                .andExpect(status().isMethodNotAllowed());
    }

    private ResultActions checkEmail(String email) throws Exception {
        return mockMvc.perform(post(EMAIL_CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions signup(String email) throws Exception {
        return mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"nickname\":\"tester\"}".formatted(email)));
    }
}
