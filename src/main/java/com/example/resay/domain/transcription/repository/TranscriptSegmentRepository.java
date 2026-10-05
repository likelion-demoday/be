package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByRecordingIdOrderBySegmentNo(Long recordingId);
}
