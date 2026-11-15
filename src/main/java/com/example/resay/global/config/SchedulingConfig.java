package com.example.resay.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 원본 데이터 정리 재시도처럼 주기적으로 실행하는 작업을 켠다
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
