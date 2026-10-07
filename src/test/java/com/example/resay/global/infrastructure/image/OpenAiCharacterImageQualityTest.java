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
                "꼬리질문 장인",
                "상대가 한 말을 놓치지 않고 질문을 덧붙이며 대화를 활발하게 이어 가는 편입니다.",
                "상대의 답변에서 새로운 질문을 자연스럽게 이어 가요.",
                "짧은 맞장구로 상대가 계속 말할 수 있게 도와요.",
                "꼬리 질문과 맞장구가 대화 전반에서 반복적으로 나타났어요."
        ),
        COUPLE_SELF(
                AnalysisScenario.COUPLE_DAILY,
                SpeakerRole.SELF,
                "포근한 안부 탐험가",
                "사소한 일상도 다정하게 물으며 상대의 하루를 세심하게 살핍니다.",
                "상대의 하루와 기분을 구체적으로 물어요.",
                "공감 표현으로 상대의 이야기를 편안하게 받아 줘요.",
                "안부 질문과 공감 반응이 여러 주제에서 함께 나타났어요."
        ),
        COUPLE_CONFLICT_PARTNER(
                AnalysisScenario.COUPLE_CONFLICT,
                SpeakerRole.PARTNER,
                "한발 물러선 방패",
                "갈등이 커지면 말을 아끼지만 관계를 회복하려는 표현을 다시 꺼내는 편입니다.",
                "갈등이 강해질 때 바로 맞서기보다 잠시 거리를 둬요.",
                "대화 후반에는 관계를 회복하려는 표현을 꺼내요.",
                "회피 반응 뒤에 회복 시도가 이어지는 흐름이 나타났어요."
        ),
        PARENT(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.PARENT,
                "걱정 많은 확성기",
                "돌봄의 마음이 크지만 걱정을 강한 표현으로 전달하는 모습이 나타났습니다.",
                "자녀의 상황을 확인하려는 질문을 반복해요.",
                "걱정이 커질수록 표현의 강도도 함께 높아져요.",
                "돌봄 질문과 강한 걱정 표현이 대화에서 함께 나타났어요."
        ),
        CHILD(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.CHILD,
                "조용한 경계선 지킴이",
                "자신의 선택을 지키고 싶을 때 짧고 단단한 말로 거리를 두는 편입니다.",
                "원하지 않는 요구에는 짧고 분명하게 선을 그어요.",
                "감정이 커질 때 설명보다 짧은 대답을 사용해요.",
                "경계를 나타내는 짧은 응답이 갈등 구간에서 반복됐어요."
        );

        private final AnalysisScenario scenario;
        private final SpeakerRole role;
        private final String name;
        private final String description;
        private final String mainTrait;
        private final String secondaryTrait;
        private final String evidence;

        CharacterCase(
                AnalysisScenario scenario,
                SpeakerRole role,
                String name,
                String description,
                String mainTrait,
                String secondaryTrait,
                String evidence
        ) {
            this.scenario = scenario;
            this.role = role;
            this.name = name;
            this.description = description;
            this.mainTrait = mainTrait;
            this.secondaryTrait = secondaryTrait;
            this.evidence = evidence;
        }

        CharacterImageGenerationCommand command() {
            return new CharacterImageGenerationCommand(
                    scenario,
                    role,
                    name,
                    description,
                    mainTrait,
                    secondaryTrait,
                    evidence
            );
        }
    }
}
