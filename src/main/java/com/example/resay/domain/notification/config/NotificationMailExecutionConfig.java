package com.example.resay.domain.notification.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 메일 발송은 분석 저장과 다른 스레드에서 한다. 메일 서비스가 느려도 분석 처리가 기다리지 않는다
@EnableAsync
@Configuration
@EnableConfigurationProperties({NotificationMailProperties.class, MarketingReminderProperties.class})
public class NotificationMailExecutionConfig {

    @Bean(name = "notificationMailTaskExecutor")
    public Executor notificationMailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-mail-");
        // 서버를 끌 때 대기 중인 메일을 보내고 끈다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
