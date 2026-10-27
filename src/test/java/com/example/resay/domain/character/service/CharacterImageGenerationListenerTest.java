package com.example.resay.domain.character.service;

import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.service.CreditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CharacterImageGenerationListenerTest {

    @Mock
    private CharacterImageProcessor characterImageProcessor;

    @Mock
    private CharacterImageService characterImageService;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private CharacterImageGenerationListener listener;

    @Test
    void processesExplicitCharacterImageRequest() {
        CharacterImageRequestedEvent event = new CharacterImageRequestedEvent(10L, 1L);

        listener.handle(event);

        then(characterImageProcessor).should().process(event);
        then(characterImageService).shouldHaveNoInteractions();
        then(creditService).should(never()).refund(
                UsagePurpose.CHARACTER,
                event.recordingId(),
                "캐릭터 이미지 생성 실패"
        );
    }

    @Test
    void refundsCharacterCreditWhenGenerationFails() {
        CharacterImageRequestedEvent event = new CharacterImageRequestedEvent(10L, 1L);
        willThrow(new CharacterImageProcessingException())
                .given(characterImageProcessor)
                .process(event);

        listener.handle(event);

        then(characterImageService).should().failInProgress(
                event.analysisId(),
                CharacterImageGenerationListener.PIPELINE_FAILURE_CODE
        );
        then(creditService).should().refund(
                UsagePurpose.CHARACTER,
                event.recordingId(),
                "캐릭터 이미지 생성 실패"
        );
    }

    @Test
    void failsInProgressImagesAndRefundsWhenValidationFails() {
        CharacterImageRequestedEvent event = new CharacterImageRequestedEvent(10L, 1L);
        willThrow(new IllegalStateException("분석과 보고서의 녹음이 일치하지 않습니다."))
                .given(characterImageProcessor)
                .process(event);

        listener.handle(event);

        then(characterImageService).should().failInProgress(
                event.analysisId(),
                CharacterImageGenerationListener.PIPELINE_FAILURE_CODE
        );
        then(creditService).should().refund(
                UsagePurpose.CHARACTER,
                event.recordingId(),
                "캐릭터 이미지 생성 실패"
        );
    }

    @Test
    void refundsEvenWhenFailingRemainingImageRowsFails() {
        CharacterImageRequestedEvent event = new CharacterImageRequestedEvent(10L, 1L);
        CharacterImageProcessingException processingException =
                new CharacterImageProcessingException();
        IllegalStateException statusException = new IllegalStateException("상태 변경 실패");
        willThrow(processingException)
                .given(characterImageProcessor)
                .process(event);
        given(characterImageService.failInProgress(
                event.analysisId(),
                CharacterImageGenerationListener.PIPELINE_FAILURE_CODE
        )).willThrow(statusException);

        listener.handle(event);

        then(creditService).should().refund(
                UsagePurpose.CHARACTER,
                event.recordingId(),
                "캐릭터 이미지 생성 실패"
        );
        assertThat(processingException.getSuppressed())
                .containsExactly(statusException);
    }
}
