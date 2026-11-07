package com.example.resay.domain.notification.controller;

import com.example.resay.domain.notification.service.UnsubscribeTokenSigner;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketingUnsubscribeControllerTest {

    private static final String URL = "/api/v1/notifications/unsubscribe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnsubscribeTokenSigner unsubscribeTokenSigner;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("unsubscribe@example.com", "encoded", "지현"));
        user.agreeToTerms(LocalDateTime.now(), true);
        userRepository.saveAndFlush(user);
    }

    private String token() {
        return unsubscribeTokenSigner.sign(user.getId());
    }

    private boolean marketingAgreed() {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(user.getId()).orElseThrow().getMarketingAgreedAt() != null;
    }

    @Test
    void 링크를_열면_로그인_없이_확인_화면을_보여주고_아직_거부하지_않는다() throws Exception {
        mockMvc.perform(get(URL).param("u", user.getId().toString()).param("t", token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("수신 거부하기")))
                .andExpect(content().string(containsString("method=\"post\"")));

        assertThat(marketingAgreed()).isTrue();
    }

    @Test
    void 확인_버튼을_누르면_마케팅_수신_동의를_철회한다() throws Exception {
        mockMvc.perform(post(URL).param("u", user.getId().toString()).param("t", token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("수신 거부가 완료됐어요")));

        assertThat(marketingAgreed()).isFalse();
    }

    @Test
    void 메일_앱의_원클릭_구독_취소로도_철회한다() throws Exception {
        mockMvc.perform(post(URL + "?u=" + user.getId() + "&t=" + token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk());

        assertThat(marketingAgreed()).isFalse();
    }

    @Test
    void 다른_사용자의_서명으로는_거부할_수_없다() throws Exception {
        User other = userRepository.saveAndFlush(User.createLocal("other-unsub@example.com", "encoded", "남"));

        mockMvc.perform(post(URL).param("u", user.getId().toString())
                        .param("t", unsubscribeTokenSigner.sign(other.getId())))
                .andExpect(status().isBadRequest());

        assertThat(marketingAgreed()).isTrue();
    }

    @Test
    void 서명이_없거나_틀리면_400() throws Exception {
        mockMvc.perform(get(URL).param("u", user.getId().toString())).andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).param("u", user.getId().toString()).param("t", "wrong"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL)).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("u", "abc").param("t", token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        assertThat(marketingAgreed()).isTrue();
    }

    @Test
    void 이미_거부한_사용자가_다시_눌러도_완료로_보여준다() throws Exception {
        mockMvc.perform(post(URL).param("u", user.getId().toString()).param("t", token()));

        mockMvc.perform(post(URL).param("u", user.getId().toString()).param("t", token()))
                .andExpect(status().isOk());
    }
}
