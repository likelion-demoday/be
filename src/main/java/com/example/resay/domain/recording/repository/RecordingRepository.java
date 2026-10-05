package com.example.resay.domain.recording.repository;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByIdAndUserId(Long id, Long userId);

    // 분석이 끝났거나 실패한 뒤 보관 기간이 지났는데 음성 파일이 남아 있는 녹음
    @Query("""
            select r.id from Recording r
            where r.audioDeletedAt is null
              and ((r.status = com.example.resay.domain.recording.entity.RecordingStatus.COMPLETED and r.completedAt < :cutoff)
                or (r.status = com.example.resay.domain.recording.entity.RecordingStatus.FAILED and r.failedAt < :cutoff))
            """)
    List<Long> findAudioExpiredIds(@Param("cutoff") LocalDateTime cutoff);

    // 결제 단계까지 가지 않고 기한이 지난 녹음
    @Query("select r.id from Recording r where r.status in :statuses and r.createdAt < :cutoff")
    List<Long> findIdsByStatusInAndCreatedAtBefore(
            @Param("statuses") Collection<RecordingStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff
    );
}
