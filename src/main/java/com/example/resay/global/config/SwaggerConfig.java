package com.example.resay.global.config;

import com.example.resay.global.security.CurrentUserId;
import org.springdoc.core.utils.SpringDocUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    static final String BEARER_AUTH = "bearerAuth";

    // @CurrentUserId 파라미터는 요청 값이 아니라 토큰에서 꺼내는 값이다.
    // 문서 생성기는 이를 모르고 필수 쿼리 파라미터(userId)로 표시하므로 문서에서 제외한다
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    // Swagger 우측 상단 Authorize 버튼에 Access Token을 한 번 넣으면 모든 API 요청에 Bearer 헤더가 붙는다
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Resay API")
                        .description("대화 습관 분석 서비스 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
