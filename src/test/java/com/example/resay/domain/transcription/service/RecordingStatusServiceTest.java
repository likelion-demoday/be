package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.dto.RecordingStep;
import com.example.resay.domain.recording.entity.RecordingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingStatusServiceTest {

    @Test
    void 녹음_상태와_전사_완료_여부로_화면_단계를_정한다() {
        assertThat(RecordingStatusService.toStep(RecordingStatus.UPLOADED, false)).isEqualTo(RecordingStep.UPLOADED);
        assertThat(RecordingStatusService.toStep(RecordingStatus.TYPE_SELECTED, false)).isEqualTo(RecordingStep.TYPE_SELECTED);
        // 결제 직후 전사 요청 전도 사용자에게는 전사 대기
        assertThat(RecordingStatusService.toStep(RecordingStatus.PAYMENT_COMPLETED, false)).isEqualTo(RecordingStep.TRANSCRIBING);
        assertThat(RecordingStatusService.toStep(RecordingStatus.TRANSCRIBING, false)).isEqualTo(RecordingStep.TRANSCRIBING);
        assertThat(RecordingStatusService.toStep(RecordingStatus.TRANSCRIBING, true))
                .isEqualTo(RecordingStep.SPEAKER_SELECTION_REQUIRED);
        assertThat(RecordingStatusService.toStep(RecordingStatus.ANALYZING, false)).isEqualTo(RecordingStep.ANALYZING);
        assertThat(RecordingStatusService.toStep(RecordingStatus.COMPLETED, false)).isEqualTo(RecordingStep.COMPLETED);
        assertThat(RecordingStatusService.toStep(RecordingStatus.FAILED, false)).isEqualTo(RecordingStep.FAILED);
    }
}
