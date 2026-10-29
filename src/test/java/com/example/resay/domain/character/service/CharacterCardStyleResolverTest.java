package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterCardStyleResolverTest {

    private final CharacterCardStyleResolver resolver = new CharacterCardStyleResolver();

    @Test
    void resolvesStableValidCardStyle() {
        var first = resolver.resolve(SpeakerRole.SELF, "질문 수집가");
        var second = resolver.resolve(SpeakerRole.SELF, "질문 수집가");

        assertThat(first).isEqualTo(second);
        assertThat(first.characterNameTextColor()).matches("^#[0-9A-F]{6}$");
        assertThat(first.cardBackgroundGradientColors())
                .hasSize(2)
                .allMatch(color -> color.matches("^#[0-9A-F]{6}$"));
    }
}
