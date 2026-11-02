package com.example.resay.global.security;

import com.example.resay.domain.user.service.UserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityInterceptor implements HandlerInterceptor {

    private final UserActivityService userActivityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            // 지표 기록이 실패해도 사용자 요청은 막지 않는다
            try {
                userActivityService.record(Long.valueOf(jwtAuthentication.getToken().getSubject()));
            } catch (RuntimeException e) {
                log.warn("일별 활동 기록에 실패했습니다.", e);
            }
        }
        return true;
    }
}
