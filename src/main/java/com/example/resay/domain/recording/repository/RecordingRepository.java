package com.example.resay.domain.recording.repository;

import com.example.resay.domain.recording.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    List<Recording> findByCreatedAtBefore(LocalDateTime dateTime);

    Optional<Recording> findByIdAndUserId(Long id, Long userId);
}
