package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class TranscriptionRepositoryTest {

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Test
    void 작업_토큰_없이_먼저_저장하고_나중에_기록할_수_있음() {
        Transcription transcription = transcriptionRepository.saveAndFlush(
                Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH));

        transcription.assignJobToken("job-token");
        transcriptionRepository.saveAndFlush(transcription);

        Transcription found = transcriptionRepository.findByRecordingId(1L).orElseThrow();
        assertThat(found.getJobToken()).isEqualTo("job-token");
        assertThat(found.getCreatedAt()).isNotNull(); // 요청 시각 기록
    }

    @Test
    void findByCallbackSecret_callback_비밀값으로_전사_요청을_조회() {
        Transcription saved = transcriptionRepository.save(
                Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH));

        Transcription found = transcriptionRepository.findByCallbackSecret(saved.getCallbackSecret()).orElseThrow();

        assertThat(found.getRecordingId()).isEqualTo(1L);
        assertThat(transcriptionRepository.findByCallbackSecret("unknown-secret")).isEmpty();
    }

    @Test
    void 같은_녹음의_전사_요청은_중복_저장되지_않음() {
        transcriptionRepository.saveAndFlush(Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH));

        assertThrows(DataIntegrityViolationException.class, () -> transcriptionRepository.saveAndFlush(
                Transcription.prepare(1L, TranscriptionProvider.CLOVA_SPEECH)));
    }
}
