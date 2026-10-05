package com.example.resay.domain.character.repository;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterImageRepository extends JpaRepository<CharacterImage, Long> {

    Optional<CharacterImage> findByAnalysisIdAndSpeakerRole(Long analysisId, SpeakerRole speakerRole);

    List<CharacterImage> findAllByAnalysisId(Long analysisId);
}
