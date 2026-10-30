package com.example.resay.domain.character.event;

import java.util.List;

public record CharacterImagesDeletedEvent(
        List<String> objectKeys
) {

    public CharacterImagesDeletedEvent {
        objectKeys = List.copyOf(objectKeys);
    }
}
