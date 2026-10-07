package com.example.resay.domain.character.model;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                "질문과 맞장구가 반복적으로 관찰됐어요."
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
                "질문이 반복적으로 관찰됐어요."
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
                "선정 근거"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
