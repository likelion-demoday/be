package com.example.resay.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// 외부 API 호출처럼 오래 걸리는 작업을 요청 스레드와 분리한다 (스프링 부트 기본 실행기 사용)
@Configuration
@EnableAsync
public class AsyncConfig {
}
