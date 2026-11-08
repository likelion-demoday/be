package com.example.resay.domain.character.port;

import com.example.resay.domain.character.model.CharacterDesignSpec;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;

public interface CharacterDesignGenerator {

    CharacterDesignSpec generate(CharacterImageGenerationCommand command);
}
