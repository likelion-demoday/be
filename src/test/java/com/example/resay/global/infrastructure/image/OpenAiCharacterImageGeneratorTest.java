package com.example.resay.global.infrastructure.image;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCharacterImageGeneratorTest {

    private final OpenAiImageProperties properties = new OpenAiImageProperties(
            "key",
            "https://api.openai.com",
            "gpt-image-2.5-flare",
            "1024x1024",
            "medium",
            "png",
            "transparent",
            Duration.ofSeconds(3),
            Duration.ofSeconds(180)
    );
    private final OpenAiImageApiClient apiClient = mock(OpenAiImageApiClient.class);
    private final OpenAiCharacterImageGenerator generator = new OpenAiCharacterImageGenerator(
            properties,
            new OpenAiCharacterPromptFactory(),
            apiClient
    );

    @Test
    void decodesGeneratedImage() {
        byte[] imageBytes = "image-content".getBytes(StandardCharsets.UTF_8);
        when(apiClient.generate(any())).thenReturn(new OpenAiImageResult(
                "request-1",
                new OpenAiImageResponse(
                        1L,
                        List.of(new OpenAiImageResponse.ImageData(
                                Base64.getEncoder().encodeToString(imageBytes)
                        )),
                        "png",
                        "medium",
                        "1024x1024"
                )
        ));

        GeneratedCharacterImage result = generator.generate(command());

        assertThat(result.content()).isEqualTo(imageBytes);
        assertThat(result.mediaType()).isEqualTo("image/png");
        assertThat(result.model()).isEqualTo("gpt-image-2.5-flare");
        assertThat(result.promptVersion()).isEqualTo("character-image-prompt-v1");
    }

    @Test
    void rejectsMalformedBase64() {
        when(apiClient.generate(any())).thenReturn(new OpenAiImageResult(
                "request-1",
                new OpenAiImageResponse(
                        1L,
                        List.of(new OpenAiImageResponse.ImageData("not-base64!")),
                        "png",
                        "medium",
                        "1024x1024"
                )
        ));

        assertThatThrownBy(() -> generator.generate(command()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("invalid_image_data")
                );
    }

    private CharacterImageGenerationCommand command() {
        return new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "질문 수집가",
                "친구의 말을 질문으로 이어 갑니다."
        );
    }
}
