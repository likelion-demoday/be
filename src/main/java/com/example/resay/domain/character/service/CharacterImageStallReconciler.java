package com.example.resay.domain.character.service;

import com.example.resay.domain.credit.config.CreditProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서버 재시작 등으로 더 이상 진행되지 않는 캐릭터 이미지 행을 실패 처리한다.
 * 환급 정리 작업과 같은 timeout을 사용해 행 상태와 크레딧 상태의 판단 기준을 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterImageStallReconciler {

    private final CharacterImageService characterImageService;
    private final CreditProperties creditProperties;

    @Scheduled(cron = "${character.image.stall-reconcile-cron:30 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minus(creditProperties.characterStallTimeout());
        try {
            int failed = characterImageService.failStalled(cutoff);
            if (failed > 0) {
                log.info("멈춘 캐릭터 이미지 정리: 실패 처리 {}건", failed);
            }
        } catch (RuntimeException exception) {
            log.warn("멈춘 캐릭터 이미지 정리에 실패했습니다. 다음 실행에서 다시 시도합니다.", exception);
        }
    }
}
