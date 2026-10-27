package com.example.resay.domain.user.repository;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    // 분석이 끝나 보고서가 있는 녹음(사용자가 지운 것 제외)의 개수와 길이 합. 녹음(B 파트)은 조회만 한다
    @Query("""
            select count(r) as analysisCount, coalesce(sum(r.durationSeconds), 0L) as totalRecordingSeconds
            from Recording r
            where r.userId = :userId
              and r.status = com.example.resay.domain.recording.entity.RecordingStatus.COMPLETED
              and r.deletedAt is null
            """)
    CompletedRecordings summarizeCompletedRecordings(@Param("userId") Long userId);

    interface CompletedRecordings {

        long getAnalysisCount();

        long getTotalRecordingSeconds();
    }
}
