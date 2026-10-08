package com.example.resay.global.infrastructure.nicepay;

import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(NicepayProperties.class)
public class NicepayClientConfig {

    // 타임아웃은 나이스페이 권장값(연결 5초, 응답 30초)을 설정으로 받는다.
    // 응답을 못 받은 승인은 결제됐는지 알 수 없으므로, 그때는 망취소를 요청한다 (NicepayClient 설명 참고)
    @Bean
    public RestClient nicepayRestClient(NicepayProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        // 결제는 드물게 일어나 연결이 오래 쉰다. 쉬던 연결이 상대 쪽에서 이미 끊겼으면 승인 요청이 "결과 모름"이 된다.
                        // 쉬는 연결을 빨리 정리하는 설정(docker-compose.prod.yml의 jdk.httpclient.keepalive.timeout)은
                        // HTTP/1.1 연결에만 적용되므로 버전을 고정한다
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(properties.connectTimeout())
                        .build()
        );
        requestFactory.setReadTimeout(properties.readTimeout());

        // 어느 상점 · 어느 주소로 붙었는지 배포 로그에서 바로 보이게 남긴다 (키가 서버에 전달되지 않은 것도 여기서 드러난다)
        if (!properties.isConfigured()) {
            log.info("나이스페이 키가 설정되지 않아 충전 기능이 꺼져 있습니다.");
        } else if (!properties.isServerApprovalKey()) {
            log.warn("나이스페이 클라이언트 키가 Server 승인 방식(S2_ · R2_)이 아닙니다. 충전 주문을 받지 않습니다.");
        } else {
            log.info("나이스페이 연동: {} ({})",
                    properties.isProductionKey() ? "운영 상점" : "테스트 상점", properties.resolvedApiBaseUrl());
        }
        return RestClient.builder()
                .baseUrl(properties.resolvedApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
