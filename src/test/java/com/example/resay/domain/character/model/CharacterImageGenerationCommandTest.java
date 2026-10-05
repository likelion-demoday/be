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
                "상대의 이야기를 질문으로 이어 가는 편입니다."
        );
    }

    @Test
    void rejectsRoleNotRequiredByScenario() {
        assertThatThrownBy(() -> new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.PARTNER,
                "질문 수집가",
                "상대의 이야기를 질문으로 이어 가는 편입니다."
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankProfile() {
        assertThatThrownBy(() -> new CharacterImageGenerationCommand(
                AnalysisScenario.FRIEND_DAILY,
                SpeakerRole.SELF,
                " ",
                "설명"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
