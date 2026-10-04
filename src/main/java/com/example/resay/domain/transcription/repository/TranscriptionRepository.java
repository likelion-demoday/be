package com.example.resay.domain.transcription.repository;

import com.example.resay.domain.transcription.entity.Transcription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    Optional<Transcription> findByRecordingId(Long recordingId);

    Optional<Transcription> findByCallbackSecret(String callbackSecret);
}
