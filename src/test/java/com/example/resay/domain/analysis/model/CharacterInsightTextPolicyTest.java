package com.example.resay.domain.analysis.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterInsightTextPolicyTest {

    @Test
    void keepsTextWithinCardLimits() {
        assertThat(CharacterInsightTextPolicy.fitName("질문 수집가"))
                .isEqualTo("질문 수집가");
        assertThat(CharacterInsightTextPolicy.fitDescription(
                "꼬리 질문으로 상대의 이야기를 이어 가요."
        )).isEqualTo("꼬리 질문으로 상대의 이야기를 이어 가요.");
    }

    @Test
    void abbreviatesLegacyTextAtNaturalBoundary() {
        String longName = "상대의 이야기를 끝까지 따라가는 호기심 많은 질문 수집가";
        String longDescription = "상대가 들려주는 이야기를 끝까지 듣고 새로운 질문을 계속 덧붙이며 대화를 자연스럽게 이어 가요.";

        assertThat(CharacterInsightTextPolicy.fitName(longName))
                .hasSizeLessThanOrEqualTo(CharacterInsightTextPolicy.NAME_MAX_LENGTH)
                .endsWith("…");
        assertThat(CharacterInsightTextPolicy.fitDescription(longDescription))
                .hasSizeLessThanOrEqualTo(CharacterInsightTextPolicy.DESCRIPTION_MAX_LENGTH)
                .endsWith("…");
    }
}
