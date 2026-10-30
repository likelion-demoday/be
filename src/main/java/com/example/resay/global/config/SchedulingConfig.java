package com.example.resay.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 보관 기간이 지난 음성 정리처럼 주기적으로 실행하는 작업을 켠다
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
