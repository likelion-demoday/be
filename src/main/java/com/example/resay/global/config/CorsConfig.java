package com.example.resay.global.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    // 반환 타입을 UrlBasedCorsConfigurationSource로 선언해야 Spring Security가 이 빈을 찾아 CORS를 자동 적용한다
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // 브라우저 스크립트가 읽을 수 있게 허용하는 응답 헤더 (시도 횟수 제한에 걸렸을 때 남은 시간)
        configuration.setExposedHeaders(List.of("Retry-After"));
        // 토큰은 Authorization 헤더로 주고받으므로 쿠키 전송은 허용하지 않는다
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
