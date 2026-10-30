package com.example.resay.global.infrastructure.storage;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableConfigurationProperties(CharacterImageStorageProperties.class)
public class LocalCharacterImageStorage implements CharacterImageStorage {

    private final Path basePath;

    public LocalCharacterImageStorage(CharacterImageStorageProperties properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath().normalize();
    }

    @Override
    public String save(Long analysisId, SpeakerRole speakerRole, GeneratedCharacterImage image) {
        String extension = extensionOf(image.mediaType());
        String objectKey = analysisId + "/"
                + speakerRole.name().toLowerCase(Locale.ROOT)
                + "-" + UUID.randomUUID() + "." + extension;
        Path target = resolve(objectKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, image.content());
            return objectKey;
        } catch (IOException exception) {
            delete(objectKey);
            throw new CharacterImageStorageException("캐릭터 이미지를 저장할 수 없습니다.", exception);
        }
    }

    @Override
    public byte[] load(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException | IllegalArgumentException exception) {
            throw new CharacterImageStorageException("캐릭터 이미지를 읽을 수 없습니다.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("캐릭터 이미지 삭제 실패: objectKey={}", objectKey, exception);
        }
    }

    private Path resolve(String objectKey) {
        Path target = basePath.resolve(objectKey).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalArgumentException("허용되지 않은 캐릭터 이미지 경로입니다.");
        }
        return target;
    }

    private String extensionOf(String mediaType) {
        return switch (mediaType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다: " + mediaType);
        };
    }
}
