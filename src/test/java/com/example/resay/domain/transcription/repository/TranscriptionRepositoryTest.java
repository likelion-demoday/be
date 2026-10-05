package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class TranscriptionRepositoryTest {

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void findTimedOutRecordingIds_전사를_시작한_지_오래됐고_결과를_받지_못한_녹음만_조회() {
        Long 결과_없음 = transcribingRecording(2);
        transcriptionRepository.save(Transcription.prepare(결과_없음, TranscriptionProvider.CLOVA_SPEECH));
        Long 요청_기록도_없음 = transcribingRecording(2); // 요청 도중 서버가 멈춘 경우
        Long 결과_받음 = transcribingRecording(2);
        Transcription completed = Transcription.prepare(결과_받음, TranscriptionProvider.CLOVA_SPEECH);
        completed.complete();
        transcriptionRepository.save(completed);
        transcribingRecording(0); // 방금 시작한 전사
        entityManager.flush();
        entityManager.clear();

        List<Long> ids = transcriptionRepository.findTimedOutRecordingIds(LocalDateTime.now().minusHours(1));

        assertThat(ids).containsExactlyInAnyOrder(결과_없음, 요청_기록도_없음);
    }

    // 전사 중인 녹음을 저장하고 마지막 상태 변경 시각을 hoursAgo 시간 전으로 맞춘다
    private Long transcribingRecording(int hoursAgo) {
        Recording recording = Recording.create(1L, "/storage/test.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        Long id = recordingRepository.saveAndFlush(recording).getId();
        entityManager.createNativeQuery("update recordings set updated_at = ? where id = ?")
                .setParameter(1, LocalDateTime.now().minusHours(hoursAgo).minusMinutes(1))
                .setParameter(2, id)
                .executeUpdate();
        return id;
    }
}
