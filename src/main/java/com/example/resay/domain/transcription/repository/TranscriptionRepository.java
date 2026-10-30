package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.Transcription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    Optional<Transcription> findByRecordingId(Long recordingId);

    Optional<Transcription> findByCallbackSecret(String callbackSecret);

    // 전사를 시작한 지 오래됐는데 결과를 받지 못한 녹음
    // (요청 도중 서버가 멈춰 전사 요청 기록이 없는 녹음도 포함한다)
    @Query("""
            select r.id from Recording r
            where r.status = com.example.resay.domain.recording.entity.RecordingStatus.TRANSCRIBING
              and r.updatedAt < :cutoff
              and not exists (
                  select t.id from Transcription t
                  where t.recordingId = r.id
                    and t.status = com.example.resay.domain.transcription.entity.TranscriptionStatus.COMPLETED
              )
            """)
    List<Long> findTimedOutRecordingIds(@Param("cutoff") LocalDateTime cutoff);

    // 전사가 끝난 지 오래됐는데 사용자가 화자를 고르지 않은 녹음
    @Query("""
            select r.id from Recording r, Transcription t
            where t.recordingId = r.id
              and r.status = com.example.resay.domain.recording.entity.RecordingStatus.TRANSCRIBING
              and t.status = com.example.resay.domain.transcription.entity.TranscriptionStatus.COMPLETED
              and t.completedAt < :cutoff
            """)
    List<Long> findSpeakerSelectionExpiredRecordingIds(@Param("cutoff") LocalDateTime cutoff);
}
