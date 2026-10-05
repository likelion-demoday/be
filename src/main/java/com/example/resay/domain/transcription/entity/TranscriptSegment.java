package com.example.resay.domain.transcription.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 화자별 발화 한 구간
// 화자는 전사 서비스가 붙인 번호 그대로 저장하고, 누가 "나"인지는 사용자가 화자를 고른 뒤 정해진다
@Getter
@Entity
@Table(
        name = "transcript_segments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transcript_segments_recording_id_segment_no",
                columnNames = {"recording_id", "segment_no"}
        ),
        indexes = @Index(name = "idx_transcript_segments_recording_id", columnList = "recording_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranscriptSegment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    // 녹음 안에서의 발화 순서 (1부터, 분석의 segmentId로 사용)
    @Column(nullable = false, updatable = false)
    private Integer segmentNo;

    @Column(nullable = false, length = 10)
    private String speakerLabel;

    @Column(nullable = false)
    private Long startMs;

    @Column(nullable = false)
    private Long endMs;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private TranscriptSegment(Long recordingId, Integer segmentNo, String speakerLabel,
                              Long startMs, Long endMs, String content) {
        this.recordingId = recordingId;
        this.segmentNo = segmentNo;
        this.speakerLabel = speakerLabel;
        this.startMs = startMs;
        this.endMs = endMs;
        this.content = content;
    }

    public static TranscriptSegment create(Long recordingId, int segmentNo, String speakerLabel,
                                           long startMs, long endMs, String content) {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        if (segmentNo <= 0) {
            throw new IllegalArgumentException("segmentNo는 1 이상이어야 합니다.");
        }
        if (speakerLabel == null || speakerLabel.isBlank()) {
            throw new IllegalArgumentException("speakerLabel은 비어 있을 수 없습니다.");
        }
        // 분석 입력(AnalysisSegment)과 같은 기준: 끝 시각은 시작 시각보다 커야 한다
        if (startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("구간 시각이 올바르지 않습니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        }
        return new TranscriptSegment(recordingId, segmentNo, speakerLabel, startMs, endMs, content);
    }
}
