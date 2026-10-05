package com.example.resay.domain.character.entity;

import com.example.resay.domain.analysis.model.SpeakerRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterImageTest {

    @Test
    void completesGeneration() {
        CharacterImage image = CharacterImage.prepare(1L, SpeakerRole.SELF);

        image.start();
        image.complete("1/self.png", "image/png", "gpt-image", "v1");

        assertThat(image.getStatus()).isEqualTo(CharacterImageStatus.COMPLETED);
        assertThat(image.getObjectKey()).isEqualTo("1/self.png");
        assertThat(image.getMediaType()).isEqualTo("image/png");
        assertThat(image.getModelName()).isEqualTo("gpt-image");
        assertThat(image.getPromptVersion()).isEqualTo("v1");
        assertThat(image.getFailureCode()).isNull();
    }

    @Test
    void recordsFailure() {
        CharacterImage image = CharacterImage.prepare(1L, SpeakerRole.SELF);

        image.start();
        image.fail("provider_error");

        assertThat(image.getStatus()).isEqualTo(CharacterImageStatus.FAILED);
        assertThat(image.getFailureCode()).isEqualTo("provider_error");
        assertThat(image.getObjectKey()).isNull();
    }

    @Test
    void rejectsCompletionBeforeGenerationStarts() {
        CharacterImage image = CharacterImage.prepare(1L, SpeakerRole.SELF);

        assertThatThrownBy(() ->
                image.complete("1/self.png", "image/png", "gpt-image", "v1")
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRestartingCompletedGeneration() {
        CharacterImage image = CharacterImage.prepare(1L, SpeakerRole.SELF);
        image.start();
        image.complete("1/self.png", "image/png", "gpt-image", "v1");

        assertThatThrownBy(image::start).isInstanceOf(IllegalStateException.class);
    }
}
