package com.example.resay.global.security;

import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import com.example.resay.global.exception.GeneralException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsOnlyAnnotatedParameter() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    void resolvesUserIdFromAccessTokenSubject() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("42")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(42L);
    }

    @Test
    void rejectsRequestWithoutAccessToken() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }

    @Test
    void rejectsNonLongParameter() {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(1), null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(
                "handle", Long.class, String.class, Long.class);
        return new MethodParameter(method, index);
    }

    @SuppressWarnings("unused")
    private static class SampleController {

        void handle(@CurrentUserId Long userId, @CurrentUserId String wrongType, Long other) {
        }
    }
}
