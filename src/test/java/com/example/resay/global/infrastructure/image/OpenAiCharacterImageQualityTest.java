package com.example.resay.global.infrastructure.image;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "OPENAI_CHARACTER_CASE",
        matches = "FRIEND_SELF|COUPLE_SELF|COUPLE_CONFLICT_PARTNER|PARENT|CHILD"
)
class OpenAiCharacterImageQualityTest {

    @Autowired
    private CharacterImageGenerator imageGenerator;

    @Test
    void generatesSelectedCharacterImage() throws Exception {
        CharacterCase selected = CharacterCase.valueOf(
                System.getenv("OPENAI_CHARACTER_CASE").toUpperCase(Locale.ROOT)
        );

        GeneratedCharacterImage image = imageGenerator.generate(selected.command());

        Path outputDirectory = Path.of("build", "reports", "openai-character-quality");
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve(
                selected.name().toLowerCase(Locale.ROOT) + "." + extension(image.mediaType())
        );
        Files.write(outputPath, image.content());

        System.out.println(outputPath.toAbsolutePath());
        System.out.println("model=" + image.model());
        System.out.println("promptVersion=" + image.promptVersion());

        assertThat(image.content()).isNotEmpty();
        assertThat(image.mediaType()).startsWith("image/");
        if (image.mediaType().equals("image/png")) {
            assertThat(image.content()).startsWith(
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
            );
        }
    }

    private String extension(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalStateException("지원하지 않는 이미지 형식입니다.");
        };
    }

    private enum CharacterCase {
        FRIEND_SELF(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "질문 수집가",
                "상대가 한 말을 놓치지 않고 질문을 덧붙이며 대화를 활발하게 이어 가는 편입니다."
        ),
        COUPLE_SELF(
                AnalysisScenario.COUPLE_DAILY,
                SpeakerRole.SELF,
                "포근한 안부 탐험가",
                "사소한 일상도 다정하게 물으며 상대의 하루를 세심하게 살핍니다."
        ),
        COUPLE_CONFLICT_PARTNER(
                AnalysisScenario.COUPLE_CONFLICT,
                SpeakerRole.PARTNER,
                "한발 물러선 방패",
                "갈등이 커지면 말을 아끼지만 관계를 회복하려는 표현을 다시 꺼내는 편입니다."
        ),
        PARENT(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.PARENT,
                "걱정 많은 확성기",
                "돌봄의 마음이 크지만 걱정을 강한 표현으로 전달하는 모습이 나타났습니다."
        ),
        CHILD(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.CHILD,
                "조용한 경계선 지킴이",
                "자신의 선택을 지키고 싶을 때 짧고 단단한 말로 거리를 두는 편입니다."
        );

        private final AnalysisScenario scenario;
        private final SpeakerRole role;
        private final String name;
        private final String description;

        CharacterCase(
                AnalysisScenario scenario,
                SpeakerRole role,
                String name,
                String description
        ) {
            this.scenario = scenario;
            this.role = role;
            this.name = name;
            this.description = description;
        }

        CharacterImageGenerationCommand command() {
            return new CharacterImageGenerationCommand(scenario, role, name, description);
        }
    }
}
