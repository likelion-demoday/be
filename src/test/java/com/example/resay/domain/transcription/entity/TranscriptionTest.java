package com.example.resay.domain.transcription.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranscriptionTest {

    @Test
    void prepare_요청마다_서로_다른_callback_비밀값을_만든다() {
        Transcription first = Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH);
        Transcription second = Transcription.prepare(2L, TranscriptionProvider.CLOVA_SPEECH);

        assertThat(first.getCallbackSecret()).isNotBlank();
        assertThat(first.getCallbackSecret()).isNotEqualTo(second.getCallbackSecret());
        assertThat(first.getJobToken()).isNull(); // 토큰은 요청이 접수된 뒤에 채워진다
    }

    @Test
    void assignJobToken_한번_기록한_토큰은_바꿀_수_없다() {
        Transcription transcription = Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH);
        transcription.assignJobToken("job-token");

        assertThrows(IllegalStateException.class, () -> transcription.assignJobToken("other-token"));
        assertThat(transcription.getJobToken()).isEqualTo("job-token");
    }

    @Test
    void assignJobToken_빈_토큰이면_예외() {
        Transcription transcription = Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH);

        assertThrows(IllegalArgumentException.class, () -> transcription.assignJobToken(" "));
    }
}
