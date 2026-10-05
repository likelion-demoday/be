package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, CharacterImageService.class})
class CharacterImageServiceIntegrationTest {

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private CharacterImageRepository characterImageRepository;

    @Test
    void persistsLifecycleAndPreventsDuplicateSpeakerWork() {
        assertThat(characterImageService.begin(10L, SpeakerRole.SELF)).isTrue();
        assertThat(characterImageService.begin(10L, SpeakerRole.SELF)).isFalse();

        GeneratedCharacterImage generatedImage = new GeneratedCharacterImage(
                new byte[]{1, 2, 3},
                "image/png",
                "gpt-image",
                "character-image-v1"
        );
        characterImageService.complete(
                10L,
                SpeakerRole.SELF,
                "10/self.png",
                generatedImage
        );

        var saved = characterImageRepository
                .findByAnalysisIdAndSpeakerRole(10L, SpeakerRole.SELF)
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CharacterImageStatus.COMPLETED);
        assertThat(saved.getObjectKey()).isEqualTo("10/self.png");
        assertThat(saved.getModelName()).isEqualTo("gpt-image");
        assertThat(characterImageRepository.findAllByAnalysisId(10L)).hasSize(1);
    }
}
