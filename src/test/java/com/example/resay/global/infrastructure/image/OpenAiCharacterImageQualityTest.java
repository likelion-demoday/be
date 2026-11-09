package com.example.resay.global.infrastructure.image;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.service.CharacterCardStyleResolver;
import com.example.resay.domain.character.model.CharacterDesignSpec;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterDesignGenerator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LINER_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "OPENAI_CHARACTER_CASE",
        matches = "FRIEND_SELF|COUPLE_SELF|COUPLE_CONFLICT_PARTNER|PARENT|CHILD"
)
class OpenAiCharacterImageQualityTest {

    @Autowired
    private OpenAiCharacterImageGenerator imageGenerator;

    @Autowired
    private CharacterDesignGenerator designGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CharacterCardStyleResolver characterCardStyleResolver;

    @Test
    void generatesSelectedCharacterImage() throws Exception {
        CharacterCase selected = CharacterCase.valueOf(
                System.getenv("OPENAI_CHARACTER_CASE").toUpperCase(Locale.ROOT)
        );

        CharacterImageGenerationCommand command = selected.command(characterCardStyleResolver);
        CharacterDesignSpec design = designGenerator.generate(command);
        assertThat(command.conversationKeywords())
                .anyMatch(keyword -> design.designConcept().startsWith("KEYWORD: " + keyword + ";"));
        assertThat(design.props())
                .as("대화 키워드에 근거한 대표 소품이 필요합니다")
                .isNotEmpty();
        GeneratedCharacterImage image = imageGenerator.generate(command, design);

        Path outputDirectory = Path.of("build", "reports", "openai-character-quality");
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve(
                selected.name().toLowerCase(Locale.ROOT) + "." + extension(image.mediaType())
        );
        Files.write(outputPath, image.content());
        Path designPath = outputDirectory.resolve(
                selected.name().toLowerCase(Locale.ROOT) + "-design.json"
        );
        Files.writeString(
                designPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(design),
                StandardCharsets.UTF_8
        );

        System.out.println(outputPath.toAbsolutePath());
        System.out.println(designPath.toAbsolutePath());
        System.out.println("model=" + image.model());
        System.out.println("promptVersion=" + image.promptVersion());

        assertThat(image.content()).isNotEmpty();
        assertThat(image.mediaType()).startsWith("image/");
        if (image.mediaType().equals("image/png")) {
            assertThat(image.content()).startsWith(
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
            );
            assertTransparentComposition(image.content());
        }
    }

    private void assertTransparentComposition(byte[] content) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(1024);
        assertThat(image.getHeight()).isEqualTo(1024);
        assertThat(image.getColorModel().hasAlpha()).isTrue();

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        boolean hasTransparentPixel = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha == 0) {
                    hasTransparentPixel = true;
                }
                if (alpha >= 16) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        assertThat(hasTransparentPixel).isTrue();
        assertThat(maxX).isGreaterThan(minX);
        assertThat(maxY).isGreaterThan(minY);

        double widthRatio = (double) (maxX - minX + 1) / image.getWidth();
        double heightRatio = (double) (maxY - minY + 1) / image.getHeight();
        double centerXRatio = (double) (minX + maxX) / 2 / image.getWidth();
        double centerYRatio = (double) (minY + maxY) / 2 / image.getHeight();
        assertThat(widthRatio).isBetween(0.50, 0.85);
        assertThat(heightRatio).isBetween(0.65, 0.90);
        assertThat(centerXRatio).isBetween(0.42, 0.58);
        assertThat(centerYRatio).isBetween(0.42, 0.62);
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
                "버그추적 개발자",
                "코드 흐름을 차근차근 살펴요.",
                "복잡한 문제를 작은 단위로 나눠 해결해요.",
                "오류를 발견하면 원인을 찾을 때까지 집중해요.",
                "코드 흐름을 정리하고 오류를 추적하는 모습이 반복됐어요.",
                List.of("컴퓨터공학", "코딩", "디버깅")
        ),
        COUPLE_SELF(
                AnalysisScenario.COUPLE_DAILY,
                SpeakerRole.SELF,
                "포근한 곰돌이",
                "따뜻한 안부와 공감으로 하루를 받아줘요.",
                "상대의 하루와 기분을 다정하고 구체적으로 물어봐요.",
                "감정에 맞장구치며 이야기를 편안하게 받아줘요.",
                "안부 질문과 부드러운 공감이 여러 주제에서 이어졌어요.",
                List.of("커피", "카페", "퇴근")
        ),
        COUPLE_CONFLICT_PARTNER(
                AnalysisScenario.COUPLE_CONFLICT,
                SpeakerRole.PARTNER,
                "조심스런 수달",
                "한걸음 물러선 뒤 대화 방향을 다시 맞춰요.",
                "갈등이 거세지면 바로 맞서기보다 상황을 살펴요.",
                "대화 후반에는 관계를 풀려는 말을 조심스럽게 건네요.",
                "거리 두기 뒤에 대화 방향을 되찾으려는 시도가 이어졌어요.",
                List.of("여행", "약속", "메시지")
        ),
        PARENT(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.PARENT,
                "분주한 미어캣",
                "걱정되는 순간마다 확인하는 말을 서둘러 건네요.",
                "자녀가 안전한지 확인하려는 질문을 여러 번 건네요.",
                "답을 듣지 못하면 같은 걱정을 더 크고 빠르게 말해요.",
                "안전을 확인하는 질문과 다급한 걱정 표현이 함께 나타났어요.",
                List.of("학교", "귀가", "우산")
        ),
        CHILD(
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                SpeakerRole.CHILD,
                "새침한 고양이",
                "짧고 또렷한 말로 자신만의 선을 지켜요.",
                "원하지 않는 요구에는 짧고 분명한 말로 선을 그어요.",
                "감정이 커질수록 긴 설명보다 단호한 대답을 사용해요.",
                "자신의 선택을 지키는 짧은 응답이 갈등 구간에 반복됐어요.",
                List.of("게임", "헤드폰", "컴퓨터")
        );

        private final AnalysisScenario scenario;
        private final SpeakerRole role;
        private final String name;
        private final String description;
        private final String mainTrait;
        private final String secondaryTrait;
        private final String evidence;
        private final List<String> conversationKeywords;

        CharacterCase(
                AnalysisScenario scenario,
                SpeakerRole role,
                String name,
                String description,
                String mainTrait,
                String secondaryTrait,
                String evidence,
                List<String> conversationKeywords
        ) {
            this.scenario = scenario;
            this.role = role;
            this.name = name;
            this.description = description;
            this.mainTrait = mainTrait;
            this.secondaryTrait = secondaryTrait;
            this.evidence = evidence;
            this.conversationKeywords = conversationKeywords;
        }

        CharacterImageGenerationCommand command(CharacterCardStyleResolver styleResolver) {
            return new CharacterImageGenerationCommand(
                    scenario,
                    role,
                    name,
                    description,
                    mainTrait,
                    secondaryTrait,
                    evidence,
                    conversationKeywords,
                    styleResolver.resolve(role, name)
            );
        }
    }
}
