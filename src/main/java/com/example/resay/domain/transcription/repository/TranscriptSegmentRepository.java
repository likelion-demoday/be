package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByRecordingIdOrderBySegmentNo(Long recordingId);

    @Query("select distinct s.speakerLabel from TranscriptSegment s where s.recordingId = :recordingId")
    List<String> findSpeakerLabels(@Param("recordingId") Long recordingId);
}
