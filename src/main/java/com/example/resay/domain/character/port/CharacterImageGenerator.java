package com.example.resay.domain.character.port;

import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;

public interface CharacterImageGenerator {

    GeneratedCharacterImage generate(CharacterImageGenerationCommand command);
}
