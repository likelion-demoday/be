package com.example.resay.global.infrastructure.image;

import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageGenerator;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCharacterImageGenerator implements CharacterImageGenerator {

    private final OpenAiImageProperties properties;
    private final OpenAiCharacterPromptFactory promptFactory;
    private final OpenAiImageApiClient apiClient;

    public OpenAiCharacterImageGenerator(
            OpenAiImageProperties properties,
            OpenAiCharacterPromptFactory promptFactory,
            OpenAiImageApiClient apiClient
    ) {
        this.properties = properties;
        this.promptFactory = promptFactory;
        this.apiClient = apiClient;
    }

    @Override
    public GeneratedCharacterImage generate(CharacterImageGenerationCommand command) {
        OpenAiCharacterPromptFactory.PromptSpec promptSpec = promptFactory.create(command);
        OpenAiImageRequest request = new OpenAiImageRequest(
                properties.model(),
                promptSpec.prompt(),
                1,
                properties.size(),
                properties.quality(),
                properties.outputFormat(),
                properties.background(),
                "auto"
        );
        OpenAiImageResult result = apiClient.generate(request);

        return new GeneratedCharacterImage(
                decode(result),
                mediaType(properties.outputFormat()),
                properties.model(),
                promptSpec.version()
        );
    }

    private byte[] decode(OpenAiImageResult result) {
        try {
            return Base64.getDecoder().decode(result.base64Image());
        } catch (IllegalArgumentException exception) {
            throw new OpenAiImageApiException(
                    null,
                    "invalid_image_data",
                    true,
                    null,
                    result.requestId(),
                    null,
                    exception
            );
        }
    }

    private String mediaType(String outputFormat) {
        return switch (outputFormat) {
            case "png" -> "image/png";
            case "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> throw new IllegalStateException("지원하지 않는 이미지 형식입니다.");
        };
    }
}
