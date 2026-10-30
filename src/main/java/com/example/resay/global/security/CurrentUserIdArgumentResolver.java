package com.example.resay.global.security;

import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class);
    }

    @Override
    public Long resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        if (!Long.class.equals(parameter.getParameterType())) {
            throw new IllegalStateException("@CurrentUserId는 Long 타입 파라미터에만 사용할 수 있습니다.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 인증 없이 허용된 API에 붙였거나 토큰 없이 들어온 경우
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new GeneralException(GeneralErrorCode.UNAUTHORIZED);
        }
        return Long.valueOf(jwtAuthentication.getToken().getSubject());
    }
}
