package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.CharacterCardStyle;
import com.example.resay.domain.character.model.CharacterDesignSpec;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.service.CharacterDesignGenerationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static com.example.resay.domain.character.support.CharacterDesignFixtures.validDesign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinerCharacterDesignGeneratorTest {

    private final LinerApiClient apiClient = mock(LinerApiClient.class);
    private final LinerCharacterDesignRequestFactory requestFactory =
            mock(LinerCharacterDesignRequestFactory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinerCharacterDesignGenerator generator =
            new LinerCharacterDesignGenerator(apiClient, requestFactory, objectMapper);

    @Test
    void parsesCompletedCharacterDesign() throws Exception {
        CharacterDesignSpec expected = validDesign();
        when(requestFactory.create(any())).thenReturn(mock(LinerChatRequest.class));
        when(apiClient.chat(any())).thenReturn(result(
                objectMapper.writeValueAsString(expected),
                "stop"
        ));

        CharacterDesignSpec actual = generator.generate(command());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void restoresReportCharacterName() throws Exception {
        CharacterDesignSpec source = validDesign();
        CharacterDesignSpec renamed = new CharacterDesignSpec(
                "다른 이름", source.primaryConversationPattern(),
                source.secondaryConversationPattern(), source.designConcept(),
                source.creatureType(), source.bodyDescription(), source.outfit(),
                source.props(), source.palette(), source.pose(), source.imagePrompt()
        );
        when(requestFactory.create(any())).thenReturn(mock(LinerChatRequest.class));
        when(apiClient.chat(any())).thenReturn(result(
                objectMapper.writeValueAsString(renamed),
                "stop"
        ));

        CharacterDesignSpec actual = generator.generate(command());

        assertThat(actual.characterName()).isEqualTo("질문 수집가");
    }

    @Test
    void retriesIncompleteResponseOnce() throws Exception {
        when(requestFactory.create(any())).thenReturn(mock(LinerChatRequest.class));
        when(requestFactory.createRetry(any())).thenReturn(mock(LinerChatRequest.class));
        when(apiClient.chat(any()))
                .thenReturn(result(
                        objectMapper.writeValueAsString(validDesign()),
                        "length"
                ))
                .thenReturn(result(
                        objectMapper.writeValueAsString(validDesign()),
                        "stop"
                ));

        CharacterDesignSpec actual = generator.generate(command());

        assertThat(actual).isEqualTo(validDesign());
        verify(apiClient, times(2)).chat(any());
        verify(requestFactory).createRetry(any());
    }

    @Test
    void retriesWhenConversationKeywordPropIsMissing() throws Exception {
        CharacterDesignSpec grounded = groundedDesign("코딩");
        when(requestFactory.create(any())).thenReturn(mock(LinerChatRequest.class));
        when(requestFactory.createRetry(any())).thenReturn(mock(LinerChatRequest.class));
        when(apiClient.chat(any()))
                .thenReturn(result(
                        objectMapper.writeValueAsString(validDesign()),
                        "stop"
                ))
                .thenReturn(result(
                        objectMapper.writeValueAsString(grounded),
                        "stop"
                ));

        CharacterDesignSpec actual = generator.generate(commandWithKeywords());

        assertThat(actual.designConcept()).startsWith("KEYWORD: 코딩;");
        assertThat(actual.props()).isNotEmpty();
        verify(apiClient, times(2)).chat(any());
        verify(requestFactory).createRetry(any());
    }

    @Test
    void failsAfterOneValidationRetry() throws Exception {
        when(requestFactory.create(any())).thenReturn(mock(LinerChatRequest.class));
        when(requestFactory.createRetry(any())).thenReturn(mock(LinerChatRequest.class));
        when(apiClient.chat(any())).thenReturn(result(
                objectMapper.writeValueAsString(validDesign()),
                "length"
        ));

        assertThatThrownBy(() -> generator.generate(command()))
                .isInstanceOfSatisfying(CharacterDesignGenerationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("incomplete_character_design")
                );
        verify(apiClient, times(2)).chat(any());
    }

    private LinerChatResult result(String content, String finishReason) {
        return new LinerChatResult(
                "request-1",
                new LinerChatResponse(
                        "completion-1",
                        "liner-mark-1.1",
                        List.of(new LinerChatResponse.Choice(
                                0,
                                new LinerChatMessage("assistant", content),
                                finishReason
                        )),
                        new LinerChatResponse.Usage(100, 20, 120)
                )
        );
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

    private CharacterImageGenerationCommand commandWithKeywords() {
        return new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "버그추적 개발자",
                "코드의 흐름을 차근차근 살펴요.",
                "복잡한 문제를 작은 단위로 나눠 해결해요.",
                "오류 원인을 찾을 때까지 집중해요.",
                "코딩과 디버깅 이야기가 반복됐어요.",
                List.of("코딩", "디버깅", "프로젝트"),
                new CharacterCardStyle("#6D3A2A", List.of("#FFF5EE", "#F3D2C1"))
        );
    }

    private CharacterDesignSpec groundedDesign(String keyword) {
        CharacterDesignSpec source = validDesign();
        return new CharacterDesignSpec(
                source.characterName(),
                source.primaryConversationPattern(),
                source.secondaryConversationPattern(),
                "KEYWORD: " + keyword + "; compact coding workstation concept",
                source.creatureType(),
                source.bodyDescription(),
                new CharacterDesignSpec.Outfit(
                        "relaxed charcoal zip hoodie over a simple crewneck tee",
                        "clean straight-leg cotton trousers",
                        List.of("brushed cotton jersey", "soft cotton twill"),
                        null
                ),
                List.of(new CharacterDesignSpec.Prop(
                        "Open compact laptop",
                        "brushed aluminum",
                        "supported on both forearms with the display facing inward toward the "
                                + "character and the outer lid facing the viewer"
                )),
                source.palette(),
                source.pose(),
                "Modern campus developer character holding one compact laptop with the screen "
                        + "facing the character, "
                        + String.join(", ", source.palette().hexColors())
        );
    }
}
