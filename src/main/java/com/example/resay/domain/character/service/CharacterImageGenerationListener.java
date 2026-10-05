package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "character.image",
        name = "generation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CharacterImageGenerationListener {

    private final CharacterImageProcessor characterImageProcessor;

    @Async("characterImageTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnalysisCompletedEvent event) {
        try {
            characterImageProcessor.process(event);
        } catch (RuntimeException exception) {
            log.error(
                    "캐릭터 이미지 파이프라인 실행 실패: analysisId={}, recordingId={}",
                    event.analysisId(),
                    event.recordingId(),
                    exception
            );
        }
    }
}
