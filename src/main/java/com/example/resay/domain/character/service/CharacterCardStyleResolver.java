package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.CharacterCardStyle;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CharacterCardStyleResolver {

    private static final List<CharacterCardStyle> PALETTES = List.of(
            style("#6D3A2A", "#FFF5EE", "#F3D2C1"),
            style("#5D4632", "#FFF8E8", "#EAD9B8"),
            style("#594766", "#F9F4FC", "#DED2EA"),
            style("#365B55", "#F1FAF7", "#CDE5DE"),
            style("#3F526B", "#F3F7FC", "#D3DEEC")
    );

    public CharacterCardStyle resolve(SpeakerRole speakerRole, String characterName) {
        if (speakerRole == null) {
            throw new IllegalArgumentException("speakerRole은 비어 있을 수 없습니다.");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName은 비어 있을 수 없습니다.");
        }

        String key = speakerRole.name() + ':' + characterName.strip();
        return PALETTES.get(Math.floorMod(key.hashCode(), PALETTES.size()));
    }

    private static CharacterCardStyle style(
            String textColor,
            String gradientStart,
            String gradientEnd
    ) {
        return new CharacterCardStyle(
                textColor,
                List.of(gradientStart, gradientEnd)
        );
    }
}
