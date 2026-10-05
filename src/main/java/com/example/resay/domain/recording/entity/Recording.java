package com.example.resay.domain.recording.entity;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.entity.BaseEntity;
import com.example.resay.global.exception.GeneralException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "recordings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recording extends BaseEntity {

    private static final DateTimeFormatter TITLE_DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private String title;

    @Column(nullable = false)
    private String audioFilePath;

    // 업로드 시 읽은 재생시간 (보고서의 녹음 길이 표시에 사용)
    @Column(nullable = false)
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private RecordingStatus status;

    private Recording(Long userId, String audioFilePath, Integer durationSeconds) {
        this.userId = userId;
        this.title = LocalDate.now().format(TITLE_DATE_FORMAT) + " 녹음";
        this.audioFilePath = audioFilePath;
        this.durationSeconds = durationSeconds;
        this.status = RecordingStatus.UPLOADED;
    }

    public static Recording create(Long userId, String audioFilePath, Integer durationSeconds) {
        if (userId == null) {
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
        if (audioFilePath == null || audioFilePath.isBlank()) {
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
        if (durationSeconds == null || durationSeconds <= 0) {
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
        return new Recording(userId, audioFilePath, durationSeconds);
    }

    public void selectType(RelationshipType relationshipType) {
        if (this.status != RecordingStatus.UPLOADED) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (relationshipType == null) {
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
        this.relationshipType = relationshipType;
        this.title = relationshipType.displayName() + " - " + getCreatedAt().toLocalDate().format(TITLE_DATE_FORMAT);
        this.status = RecordingStatus.TYPE_SELECTED;
    }

    public void startTranscribing() {
        if (this.status != RecordingStatus.PAYMENT_COMPLETED) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = RecordingStatus.TRANSCRIBING;
    }

    public void fail() {
        if (this.status == RecordingStatus.COMPLETED) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = RecordingStatus.FAILED;
    }
}
