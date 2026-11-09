package com.example.resay.domain.character.model;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CharacterImageGenerationCommandTest {

    @Test
    void acceptsRoleRequiredByScenario() {
        new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "질문 수집가",
                "상대의 이야기를 질문으로 이어 가는 편입니다.",
                "상대의 말에 꼬리 질문을 덧붙여요.",
                "짧게 맞장구쳐요.",
                "질문과 맞장구가 반복적으로 관찰됐어요.",
                cardStyle()
        );
    }

    @Test
    void rejectsRoleNotRequiredByScenario() {
        assertThatThrownBy(() -> new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.PARTNER,
                "질문 수집가",
                "상대의 이야기를 질문으로 이어 가는 편입니다.",
                "질문을 덧붙여요.",
                "맞장구쳐요.",
                "질문이 반복적으로 관찰됐어요.",
                cardStyle()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankProfile() {
        assertThatThrownBy(() -> new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                " ",
                "설명",
                "핵심 특징",
                null,
                "선정 근거",
                cardStyle()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesConversationKeywords() {
        CharacterImageGenerationCommand command = new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                "버그추적 개발자",
                "문제를 차근차근 해결해요.",
                "코드의 흐름을 살펴요.",
                "오류 원인을 찾아요.",
                "코딩 이야기가 반복됐어요.",
                List.of(" 컴퓨터공학 ", "코딩", "컴퓨터공학", "디버깅", "프로젝트", "노트북"),
                cardStyle()
        );

        assertThat(command.conversationKeywords())
                .containsExactly("컴퓨터공학", "코딩", "디버깅", "프로젝트", "노트북");
    }

    private CharacterCardStyle cardStyle() {
        return new CharacterCardStyle("#6D3A2A", List.of("#FFF5EE", "#F3D2C1"));
    }
}
