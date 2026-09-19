package com.example.resay.global.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityErrorResponseTest {

    private static final String PROTECTED_URL = "/api/v1/protected-resource";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityExceptionDelegator securityExceptionDelegator;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    void returnsCommonResponseWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void returnsInvalidTokenCodeWhenTokenIsMalformed() throws Exception {
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401_2"));
    }

    @Test
    void returnsInvalidTokenCodeWhenTokenIsExpired() throws Exception {
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_2"));
    }

    @Test
    void publicEndpointIgnoresExpiredTokenInHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "password123", "nickname": "닉네임"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_1"));
    }

    @Test
    void accessDeniedIsConvertedToCommonResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_URL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityExceptionDelegator.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"COMMON403_1\"");
    }

    @Test
    void registersCurrentUserIdArgumentResolver() {
        assertThat(handlerAdapter.getCustomArgumentResolvers())
                .anyMatch(CurrentUserIdArgumentResolver.class::isInstance);
    }

    private String expiredToken() {
        Instant issuedAt = Instant.now().minus(3, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("resay")
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .claim(JwtTokenProvider.ROLE_CLAIM, "USER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
