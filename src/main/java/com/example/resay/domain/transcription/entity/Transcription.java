package com.example.resay.domain.transcription.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "transcriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transcriptions_recording_id", columnNames = "recording_id"),
                @UniqueConstraint(name = "uk_transcriptions_callback_secret", columnNames = "callback_secret")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transcription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private TranscriptionProvider provider;

    // callback 주소에 넣어 결과가 우리가 요청한 작업에서 왔는지 확인하는 값
    // 작업 토큰보다 먼저 정해지므로, 토큰 저장 전에 callback이 도착해도 녹음을 찾을 수 있다
    @Column(name = "callback_secret", nullable = false, updatable = false, length = 36)
    private String callbackSecret;

    // 외부 전사 서비스의 작업 식별자 (요청이 접수된 뒤에 채워진다)
    private String jobToken;

    private Transcription(Long recordingId, TranscriptionProvider provider) {
        this.recordingId = recordingId;
        this.provider = provider;
        this.callbackSecret = UUID.randomUUID().toString();
    }

    public static Transcription prepare(Long recordingId, TranscriptionProvider provider) {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider는 비어 있을 수 없습니다.");
        }
        return new Transcription(recordingId, provider);
    }

    public void assignJobToken(String jobToken) {
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("jobToken은 비어 있을 수 없습니다.");
        }
        if (this.jobToken != null) {
            throw new IllegalStateException("이미 작업 토큰이 저장된 전사 요청입니다.");
        }
        this.jobToken = jobToken;
    }
}
