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
        assertThat(factory.promptVersion()).isEqualTo("character-design-prompt-v13");
        assertThat(factory.schemaVersion()).isEqualTo("character-design-result-v2");
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).content())
                .contains("Design ONE unique premium 3D collectible")
                .contains("Follow requiredCreatureType and requiredSilhouetteDirection")
                .contains("ANIMAL uses requiredCreatureDirection")
                .contains("COLOR — CRITICAL")
                .contains("Only BODY must have low-to-medium saturation")
                .contains("Clothing and accents have NO saturation restriction")
                .contains("harmonious, fashion-forward color combinations")
                .contains("CLOTHING — HIGHEST PRIORITY")
                .contains("variety-oriented default")
                .contains("not a hard")
                .contains("Do not turn a casual interest into an occupational uniform")
                .contains("Suits need accurate tailoring")
                .contains("nylon shirts need realistic")
                .contains("Fully transparent alpha");
        assertThat(request.messages().get(0).content())
                .contains("Short dense matte velvet micro-fuzz")
                .contains("CROWN QUALITY — CRITICAL")
                .contains("Never create one large side")
                .contains("rock-like lumps")
                .contains("1-2 tasteful wearable accessories")
                .contains("Use no more than two supporting props")
                .contains("conversation_keywords as the ONLY allowed source for props")
                .contains("computer science/coding/debugging/software project")
                .contains("one open")
                .contains("compact laptop only")
                .contains("infer one plausible real-world activity context")
                .contains("Keep styling contemporary, polished, and naturally wearable")
                .contains("Apply real-world ergonomics to every prop")
                .contains("working surface, or display must face the character")
                .contains("Respect real hinges, gravity, grip points, straps, and weight")
                .contains("create one clearly recognizable primary prop")
                .contains("KEYWORD: <exact selected keyword>")
                .contains("allowed only when the input list itself is empty")
                .contains("the primary prop could be removed")
                .contains("shared activity context")
                .contains("could not physically use, hold")
                .contains("recent_character_designs")
                .contains("generic bears");

        Map<?, ?> input = objectMapper.readValue(
                request.messages().get(1).content(),
                Map.class
        );
        assertThat(input.get("characterName")).isEqualTo("질문 수집가");
        assertThat(input.get("scenario")).isEqualTo("FRIEND_DAILY");
        assertThat(input.get("speakerRole")).isEqualTo("SELF");
        assertThat(input.get("conversation_keywords"))
                .isEqualTo(List.of("코딩", "디버깅", "프로젝트"));
        assertThat(input.get("cardBackgroundGradientColors"))
                .isEqualTo(List.of("#FFF5EE", "#F3D2C1"));
        assertThat(input.get("requiredCreatureType"))
                .isIn("ABSTRACT", "ANIMAL");
        if ("ANIMAL".equals(input.get("requiredCreatureType"))) {
            assertThat(input.get("requiredCreatureDirection"))
                    .asString()
                    .contains(":");
        } else {
            assertThat(input.get("requiredCreatureDirection")).isNull();
        }
        assertThat(input.get("suggestedOutfitDirection"))
                .asString()
                .contains(":");
        assertThat(input.get("requiredSilhouetteDirection"))
                .asString()
                .contains(":");
        assertThat(input.get("recent_character_designs")).isEqualTo(List.of());
        assertThat(input.containsKey("recentCharacterDesigns")).isFalse();

        Map<?, ?> jsonSchema = (Map<?, ?>) request.responseFormat().get("json_schema");
        assertThat(jsonSchema.get("name")).isEqualTo("character_design");
        assertThat(jsonSchema.get("strict")).isEqualTo(true);
        Map<?, ?> root = (Map<?, ?>) jsonSchema.get("schema");
        Map<?, ?> properties = (Map<?, ?>) root.get("properties");
        Map<?, ?> props = (Map<?, ?>) properties.get("props");
        assertThat(props.get("minItems")).isEqualTo(0);
        assertThat(props.get("maxItems")).isEqualTo(3);
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
                List.of("코딩", "디버깅", "프로젝트"),
                new CharacterCardStyle("#6D3A2A", List.of("#FFF5EE", "#F3D2C1"))
        );
    }
}
