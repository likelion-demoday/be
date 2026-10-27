package com.example.resay.domain.character.repository;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterImageRepository extends JpaRepository<CharacterImage, Long> {

    Optional<CharacterImage> findByAnalysisIdAndSpeakerRole(Long analysisId, SpeakerRole speakerRole);

    List<CharacterImage> findAllByAnalysisId(Long analysisId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image from CharacterImage image
            where image.status in :statuses
              and image.updatedAt < :cutoff
            order by image.id
            """)
    List<CharacterImage> findStalledForUpdate(
            @Param("statuses") Collection<CharacterImageStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff
    );
}
