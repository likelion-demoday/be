package com.example.resay.domain.character.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static com.example.resay.domain.character.support.CharacterDesignFixtures.validDesign;
import static org.assertj.core.api.Assertions.assertThat;

class CharacterDesignSpecTest {

    @Test
    void acceptsCompleteDesign() {
        CharacterDesignSpec design = validDesign();

        assertThat(design.palette().hexColors())
                .containsExactly("#287B78", "#F4E7CF", "#C9633B", "#D7A62A");
        assertThat(design.outfit().headwear()).isNull();
    }

    @Test
    void repairsDuplicatePaletteColors() {
        CharacterDesignSpec.Palette palette = new CharacterDesignSpec.Palette(
                new CharacterDesignSpec.Color("body", "#287B78"),
                new CharacterDesignSpec.Color("outfit a", "#F4E7CF"),
                new CharacterDesignSpec.Color("outfit b", "#C9633B"),
                new CharacterDesignSpec.Color("accent", "#287B78")
        );

        assertThat(palette.hexColors()).doesNotHaveDuplicates().hasSize(4);
    }

    @Test
    void appendsPaletteColorMissingFromImagePrompt() {
        CharacterDesignSpec valid = validDesign();
        String missingAccent = valid.imagePrompt().replace("#D7A62A", "mustard yellow");

        CharacterDesignSpec repaired = copy(valid, valid.props(), missingAccent);

        assertThat(repaired.imagePrompt()).contains("#D7A62A");
    }

    @Test
    void acceptsDesignWithoutProps() {
        CharacterDesignSpec valid = validDesign();

        CharacterDesignSpec repaired = copy(valid, List.of(), valid.imagePrompt());

        assertThat(repaired.props()).isEmpty();
    }

    @Test
    void buildsFallbackImagePromptWhenMissing() {
        CharacterDesignSpec valid = validDesign();

        CharacterDesignSpec repaired = copy(valid, valid.props(), null);

        assertThat(repaired.imagePrompt())
                .contains("Create one cute premium 3D collectible character")
                .contains("#287B78", "#F4E7CF", "#C9633B", "#D7A62A");
    }

    private CharacterDesignSpec copy(
            CharacterDesignSpec source,
            List<CharacterDesignSpec.Prop> props,
            String imagePrompt
    ) {
        return new CharacterDesignSpec(
                source.characterName(), source.primaryConversationPattern(),
                source.secondaryConversationPattern(), source.designConcept(),
                source.creatureType(), source.bodyDescription(), source.outfit(),
                props, source.palette(), source.pose(), imagePrompt
        );
    }
}
