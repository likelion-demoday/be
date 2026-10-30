package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, CharacterImageService.class})
class CharacterImageServiceIntegrationTest {

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private CharacterImageRepository characterImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsLifecycleAndPreventsDuplicateSpeakerWork() {
        assertThat(characterImageService.prepare(10L, List.of(SpeakerRole.SELF))).isTrue();
        assertThat(characterImageService.prepare(10L, List.of(SpeakerRole.SELF))).isFalse();
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

    @Test
    void preparesFailedSpeakerWorkForRetry() {
        characterImageService.prepare(10L, List.of(SpeakerRole.SELF));
        characterImageService.begin(10L, SpeakerRole.SELF);
        characterImageService.fail(10L, SpeakerRole.SELF, "provider_error");

        assertThat(characterImageService.prepare(10L, List.of(SpeakerRole.SELF))).isTrue();

        var retried = characterImageRepository
                .findByAnalysisIdAndSpeakerRole(10L, SpeakerRole.SELF)
                .orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(CharacterImageStatus.PENDING);
        assertThat(retried.getFailureCode()).isNull();
    }

    @Test
    void failsOnlyStalledInProgressImages() {
        characterImageService.prepare(10L, List.of(SpeakerRole.SELF, SpeakerRole.FRIEND));
        characterImageService.begin(10L, SpeakerRole.FRIEND);
        characterImageService.prepare(20L, List.of(SpeakerRole.SELF));
        characterImageService.prepare(30L, List.of(SpeakerRole.SELF));
        characterImageService.begin(30L, SpeakerRole.SELF);
        characterImageService.complete(
                30L,
                SpeakerRole.SELF,
                "30/self.png",
                new GeneratedCharacterImage(new byte[]{1}, "image/png", "gpt-image", "v1")
        );
        entityManager.flush();
        jdbcTemplate.update(
                "update character_images set updated_at = ? where analysis_id in (?, ?)",
                LocalDateTime.now().minusHours(3),
                10L,
                30L
        );
        entityManager.clear();

        assertThat(characterImageService.failStalled(LocalDateTime.now().minusMinutes(150)))
                .isEqualTo(2);

        var stalled = characterImageRepository.findAllByAnalysisId(10L);
        assertThat(stalled)
                .extracting(image -> image.getStatus())
                .containsOnly(CharacterImageStatus.FAILED);
        assertThat(stalled)
                .extracting(image -> image.getFailureCode())
                .containsOnly(CharacterImageService.TIMED_OUT_FAILURE_CODE);
        assertThat(characterImageRepository.findAllByAnalysisId(20L))
                .extracting(image -> image.getStatus())
                .containsExactly(CharacterImageStatus.PENDING);
        assertThat(characterImageRepository.findAllByAnalysisId(30L))
                .extracting(image -> image.getStatus())
                .containsExactly(CharacterImageStatus.COMPLETED);
    }

    @Test
    void timedOutImageCanBeRetriedOrDeleted() {
        characterImageService.prepare(10L, List.of(SpeakerRole.SELF));
        characterImageService.prepare(20L, List.of(SpeakerRole.SELF));
        jdbcTemplate.update(
                "update character_images set updated_at = ? where analysis_id in (?, ?)",
                LocalDateTime.now().minusHours(3),
                10L,
                20L
        );
        entityManager.clear();
        characterImageService.failStalled(LocalDateTime.now().minusMinutes(150));

        assertThat(characterImageService.prepare(10L, List.of(SpeakerRole.SELF))).isTrue();
        characterImageService.deleteAllByAnalysisId(20L);

        assertThat(characterImageRepository.findByAnalysisIdAndSpeakerRole(10L, SpeakerRole.SELF))
                .get()
                .extracting(image -> image.getStatus())
                .isEqualTo(CharacterImageStatus.PENDING);
        assertThat(characterImageRepository.findAllByAnalysisId(20L)).isEmpty();
    }

    @Test
    void failsOnlyInProgressImagesForAnalysis() {
        characterImageService.prepare(10L, List.of(SpeakerRole.SELF, SpeakerRole.FRIEND));
        characterImageService.begin(10L, SpeakerRole.FRIEND);
        characterImageService.prepare(20L, List.of(SpeakerRole.SELF));
        characterImageService.begin(20L, SpeakerRole.SELF);
        characterImageService.complete(
                20L,
                SpeakerRole.SELF,
                "20/self.png",
                new GeneratedCharacterImage(new byte[]{1}, "image/png", "gpt-image", "v1")
        );

        assertThat(characterImageService.failInProgress(10L, "PIPELINE_FAILED"))
                .isEqualTo(2);

        assertThat(characterImageRepository.findAllByAnalysisId(10L))
                .allSatisfy(image -> {
                    assertThat(image.getStatus()).isEqualTo(CharacterImageStatus.FAILED);
                    assertThat(image.getFailureCode()).isEqualTo("PIPELINE_FAILED");
                });
        assertThat(characterImageRepository.findAllByAnalysisId(20L))
                .allSatisfy(image -> assertThat(image.getStatus())
                        .isEqualTo(CharacterImageStatus.COMPLETED));
        assertThat(characterImageService.failInProgress(10L, "PIPELINE_FAILED"))
                .isZero();
    }
}
