package com.example.resay.global.infrastructure.storage;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCharacterImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndDeletesImageUsingObjectKey() throws Exception {
        LocalCharacterImageStorage storage = new LocalCharacterImageStorage(
                new CharacterImageStorageProperties(tempDir.toString())
        );
        GeneratedCharacterImage image = new GeneratedCharacterImage(
                new byte[]{1, 2, 3},
                "image/png",
                "gpt-image",
                "v1"
        );

        String objectKey = storage.save(10L, SpeakerRole.SELF, image);

        assertThat(objectKey).startsWith("10/self-").endsWith(".png");
        assertThat(Files.readAllBytes(tempDir.resolve(objectKey))).containsExactly(1, 2, 3);
        assertThat(storage.load(objectKey)).containsExactly(1, 2, 3);

        storage.delete(objectKey);

        assertThat(tempDir.resolve(objectKey)).doesNotExist();
    }

    @Test
    void rejectsUnsupportedMediaType() {
        LocalCharacterImageStorage storage = new LocalCharacterImageStorage(
                new CharacterImageStorageProperties(tempDir.toString())
        );
        GeneratedCharacterImage image = new GeneratedCharacterImage(
                new byte[]{1},
                "image/gif",
                "gpt-image",
                "v1"
        );

        assertThatThrownBy(() -> storage.save(10L, SpeakerRole.SELF, image))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsToLoadMissingImage() {
        LocalCharacterImageStorage storage = new LocalCharacterImageStorage(
                new CharacterImageStorageProperties(tempDir.toString())
        );

        assertThatThrownBy(() -> storage.load("10/missing.png"))
                .isInstanceOf(CharacterImageStorageException.class);
    }
}
