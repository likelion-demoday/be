package com.example.resay.domain.notification.scheduler;

import com.example.resay.domain.notification.service.MarketingReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingReminderScheduler {

    private final MarketingReminderService marketingReminderService;

    // 대화가 생기는 저녁 시간에 맞춰 매일 7시에 보낸다. 시각은 설정으로 바꾸고, 밤 9시 ~ 아침 8시는 서비스에서 막는다
    @Scheduled(cron = "${notification.reminder.cron:0 0 19 * * *}", zone = "Asia/Seoul")
    public void sendDueReminders() {
        try {
            marketingReminderService.sendDueReminders(LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.error("마케팅 리마인드 실행 실패", exception);
        }
    }
}
