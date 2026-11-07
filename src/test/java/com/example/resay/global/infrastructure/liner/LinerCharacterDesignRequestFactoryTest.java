package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.CharacterCardStyle;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LinerCharacterDesignRequestFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinerCharacterDesignRequestFactory factory =
            new LinerCharacterDesignRequestFactory(
                    new LinerProperties(
                            "key", "https://platform.liner.com", "liner-mark-1.1",
                            Duration.ofSeconds(3), Duration.ofSeconds(300), null
                    ),
                    objectMapper
            );

    @Test
    void createsStrictCharacterDesignRequest() throws Exception {
        LinerChatRequest request = factory.create(command());

        assertThat(request.model()).isEqualTo("liner-mark-1.1");
        assertThat(request.maxCompletionTokens()).isEqualTo(8192);
        assertThat(request.reasoningEffort()).isEqualTo("medium");
        assertThat(factory.promptVersion()).isEqualTo("character-design-prompt-v3");
        assertThat(factory.schemaVersion()).isEqualTo("character-design-result-v2");
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).content())
                .contains("FOUR-COLOR PALETTE — CRITICAL")
                .contains("CLOTHING — CRITICAL")
                .contains("exact fabric, collar, cuffs")
                .contains("Do not include a real name, transcript excerpts, scores")
                .contains("transparent background");
        assertThat(request.messages().get(0).content())
                .contains("cute, friendly, premium collectible appeal")
                .contains("Never create a pointed wedge")
                .contains("Any muzzle must be very short and softly rounded");

        Map<?, ?> input = objectMapper.readValue(
                request.messages().get(1).content(),
                Map.class
        );
        assertThat(input.get("characterName")).isEqualTo("질문 수집가");
        assertThat(input.get("scenario")).isEqualTo("FRIEND_DAILY");
        assertThat(input.get("speakerRole")).isEqualTo("SELF");
        assertThat(input.get("cardBackgroundGradientColors"))
                .isEqualTo(List.of("#FFF5EE", "#F3D2C1"));
        assertThat(input.get("requiredOutfitDirection"))
                .asString()
                .contains(":");
        assertThat(input.get("requiredSilhouetteDirection"))
                .asString()
                .contains(":");

        Map<?, ?> jsonSchema = (Map<?, ?>) request.responseFormat().get("json_schema");
        assertThat(jsonSchema.get("name")).isEqualTo("character_design");
        assertThat(jsonSchema.get("strict")).isEqualTo(true);
        Map<?, ?> root = (Map<?, ?>) jsonSchema.get("schema");
        Map<?, ?> properties = (Map<?, ?>) root.get("properties");
        Map<?, ?> props = (Map<?, ?>) properties.get("props");
        assertThat(props.get("minItems")).isEqualTo(0);
        assertThat(props.get("maxItems")).isEqualTo(4);
        Map<?, ?> palette = (Map<?, ?>) properties.get("palette");
        Map<?, ?> paletteProperties = (Map<?, ?>) palette.get("properties");
        assertThat(paletteProperties.keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("body", "outfit_a", "outfit_b", "accent");

        LinerChatRequest retryRequest = factory.createRetry(command());
        assertThat(retryRequest.messages().get(0).content())
                .contains("RETRY CORRECTION")
                .contains("previous response could not be parsed or was incomplete");
    }

    private CharacterImageGenerationCommand command() {
        return new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "질문 수집가",
                "친구의 말을 질문으로 이어 갑니다.",
                "질문으로 대화를 이어 가요.",
                "짧게 맞장구쳐요.",
                "질문과 맞장구가 반복적으로 관찰됐어요.",
                new CharacterCardStyle("#6D3A2A", List.of("#FFF5EE", "#F3D2C1"))
        );
    }
}
