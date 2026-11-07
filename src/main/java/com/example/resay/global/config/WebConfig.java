package com.example.resay.global.config;

import com.example.resay.global.security.CurrentUserIdArgumentResolver;
import com.example.resay.global.security.SharedLinkInterceptor;
import com.example.resay.global.security.UserActivityInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserActivityInterceptor userActivityInterceptor;
    private final SharedLinkInterceptor sharedLinkInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userActivityInterceptor);
        registry.addInterceptor(sharedLinkInterceptor).addPathPatterns(SharedLinkInterceptor.PATH_PATTERN);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }
}
