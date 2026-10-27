package com.example.resay.domain.character.service;

import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.service.CreditService;
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

    static final String PIPELINE_FAILURE_CODE = "PIPELINE_FAILED";

    private final CharacterImageProcessor characterImageProcessor;
    private final CharacterImageService characterImageService;
    private final CreditService creditService;

    @Async("characterImageTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CharacterImageRequestedEvent event) {
        try {
            characterImageProcessor.process(event);
        } catch (RuntimeException exception) {
            failInProgressImages(event, exception);
            refund(event, exception);
            log.error(
                    "캐릭터 이미지 파이프라인 실행 실패: analysisId={}, recordingId={}",
                    event.analysisId(),
                    event.recordingId(),
                    exception
            );
        }
    }

    private void failInProgressImages(
            CharacterImageRequestedEvent event,
            RuntimeException originalException
    ) {
        try {
            int failed = characterImageService.failInProgress(
                    event.analysisId(),
                    PIPELINE_FAILURE_CODE
            );
            if (failed > 0) {
                log.info(
                        "캐릭터 이미지 파이프라인 잔여 작업 실패 처리: analysisId={}, count={}",
                        event.analysisId(),
                        failed
                );
            }
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
            log.error(
                    "캐릭터 이미지 파이프라인 잔여 작업 실패 처리 실패: analysisId={}",
                    event.analysisId(),
                    statusException
            );
        }
    }

    private void refund(CharacterImageRequestedEvent event, RuntimeException originalException) {
        try {
            creditService.refund(
                    UsagePurpose.CHARACTER,
                    event.recordingId(),
                    "캐릭터 이미지 생성 실패"
            );
        } catch (RuntimeException refundException) {
            originalException.addSuppressed(refundException);
            log.error(
                    "캐릭터 이미지 생성 크레딧 환급 실패: recordingId={}",
                    event.recordingId(),
                    refundException
            );
        }
    }
}
