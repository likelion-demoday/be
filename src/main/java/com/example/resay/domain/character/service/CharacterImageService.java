package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterImageService {

    private final CharacterImageRepository characterImageRepository;

    @Transactional
    public boolean begin(Long analysisId, SpeakerRole speakerRole) {
        if (characterImageRepository.findByAnalysisIdAndSpeakerRole(analysisId, speakerRole)
                .isPresent()) {
            return false;
        }

        CharacterImage characterImage = CharacterImage.prepare(analysisId, speakerRole);
        characterImage.start();
        characterImageRepository.saveAndFlush(characterImage);
        return true;
    }

    @Transactional
    public void complete(
            Long analysisId,
            SpeakerRole speakerRole,
            String objectKey,
            GeneratedCharacterImage generatedImage
    ) {
        CharacterImage characterImage = find(analysisId, speakerRole);
        characterImage.complete(
                objectKey,
                generatedImage.mediaType(),
                generatedImage.model(),
                generatedImage.promptVersion()
        );
    }

    @Transactional
    public void fail(Long analysisId, SpeakerRole speakerRole, String failureCode) {
        find(analysisId, speakerRole).fail(failureCode);
    }

    private CharacterImage find(Long analysisId, SpeakerRole speakerRole) {
        return characterImageRepository.findByAnalysisIdAndSpeakerRole(analysisId, speakerRole)
                .orElseThrow(() -> new IllegalStateException("캐릭터 이미지 작업이 존재하지 않습니다."));
    }
}
