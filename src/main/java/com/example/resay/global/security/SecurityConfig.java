package com.example.resay.global.security;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            // Access Token이 만료된 상태에서 호출하므로 인증 없이 허용하고 Refresh Token으로 검증한다
            "/api/v1/auth/reissue",
            "/api/v1/auth/logout",
            "/api/v1/auth/oauth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // 컨테이너 헬스체크용. 상세 정보는 노출하지 않도록 설정되어 있다
            "/actuator/health",
            "/actuator/health/**",
            // 허용된 API에서 발생한 오류가 /error로 넘어갈 때 401로 바뀌지 않게 한다
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityExceptionDelegator securityExceptionDelegator
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                // 토큰 없이 보호된 API에 접근한 경우
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionDelegator)
                        .accessDeniedHandler(securityExceptionDelegator))
                // 토큰이 잘못됐거나 만료된 경우
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(securityExceptionDelegator)
                        .accessDeniedHandler(securityExceptionDelegator)
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증 없이 허용된 API는 헤더에 만료된 토큰이 붙어 와도 무시한다.
    // 프론트가 모든 요청에 토큰을 붙이는 경우 로그인·재발급 요청까지 401로 막히는 것을 방지한다.
    private BearerTokenResolver bearerTokenResolver() {
        RequestMatcher publicEndpoints = new OrRequestMatcher(Arrays.stream(PUBLIC_ENDPOINTS)
                .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
                .toList());
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> publicEndpoints.matches(request) ? null : defaultResolver.resolve(request);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtTokenProvider.ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
