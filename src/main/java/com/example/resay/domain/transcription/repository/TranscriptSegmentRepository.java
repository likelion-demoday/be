package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByRecordingIdOrderBySegmentNo(Long recordingId);

    @Query("select distinct s.speakerLabel from TranscriptSegment s where s.recordingId = :recordingId")
    List<String> findSpeakerLabels(@Param("recordingId") Long recordingId);

    // 대화 삭제 시 전사 원문을 지운다 (보고서는 별도로 저장돼 영향 없음)
    @Modifying
    @Query("delete from TranscriptSegment s where s.recordingId = :recordingId")
    int deleteByRecordingId(@Param("recordingId") Long recordingId);
}
