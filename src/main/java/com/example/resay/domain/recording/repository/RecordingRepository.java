package com.example.resay.domain.recording.repository;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByIdAndUserId(Long id, Long userId);

    // 결제 완료 전환은 "읽고 확인한 뒤 바꾸기" 대신 조건부 UPDATE 한 번으로 한다
    // UPDATE는 항상 최신 행을 잠그고 처리하므로, 같은 트랜잭션에서 먼저 읽어 둔 옛 값이나
    // MySQL 기본 격리수준의 스냅샷 읽기와 상관없이 동시에 들어온 두 요청 중 하나만 성공한다
    // (일괄 UPDATE는 감사 필드를 채우지 않아 updatedAt을 직접 넣는다. 전사 자동 시작 기준 시각이다)
    @Modifying(flushAutomatically = true)
    @Query("""
            update Recording r
               set r.status = com.example.resay.domain.recording.entity.RecordingStatus.PAYMENT_COMPLETED,
                   r.updatedAt = :now
             where r.id = :id
               and r.userId = :userId
               and r.status = com.example.resay.domain.recording.entity.RecordingStatus.TYPE_SELECTED
            """)
    int markPaymentCompleted(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 결제 전 녹음 삭제처럼 새 트랜잭션에서 처음 읽는 곳은 행을 잠그고 읽는다
    // (결제와 동시에 일어나면 먼저 잠근 쪽이 끝날 때까지 기다린 뒤 바뀐 상태를 본다)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recording r where r.id = :id")
    Optional<Recording> findByIdForUpdate(@Param("id") Long id);

    // 음성 파일이 남아 있는데 지워야 하는 녹음
    // - 분석이 끝났거나 실패한 뒤 보관 기간이 지남
    // - 사용자가 대화를 삭제했는데 그때 파일 삭제에 실패함 (보관 기간과 상관없이 바로 다시 지운다)
    @Query("""
            select r.id from Recording r
            where r.audioDeletedAt is null
              and ((r.status = com.example.resay.domain.recording.entity.RecordingStatus.COMPLETED and r.completedAt < :cutoff)
                or (r.status = com.example.resay.domain.recording.entity.RecordingStatus.FAILED and r.failedAt < :cutoff)
                or r.deletedAt is not null)
            """)
    List<Long> findAudioExpiredIds(@Param("cutoff") LocalDateTime cutoff);

    // 결제 단계까지 가지 않고 기한이 지난 녹음
    @Query("select r.id from Recording r where r.status in :statuses and r.createdAt < :cutoff")
    List<Long> findIdsByStatusInAndCreatedAtBefore(
            @Param("statuses") Collection<RecordingStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff
    );

    // 결제는 끝났는데 전사가 시작되지 않은 녹음 (상태가 바뀐 시각 = 결제 완료 시각 기준)
    @Query("""
            select r.id from Recording r
            where r.status = com.example.resay.domain.recording.entity.RecordingStatus.PAYMENT_COMPLETED
              and r.updatedAt < :cutoff
            """)
    List<Long> findTranscriptionNotStartedIds(@Param("cutoff") LocalDateTime cutoff);
}
