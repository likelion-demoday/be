package com.example.resay.domain.character.service;

import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CharacterImageGenerationListenerTest {

    @Mock
    private CharacterImageProcessor characterImageProcessor;

    @InjectMocks
    private CharacterImageGenerationListener listener;

    @Test
    void processesExplicitCharacterImageRequest() {
        CharacterImageRequestedEvent event = new CharacterImageRequestedEvent(10L, 1L);

        listener.handle(event);

        then(characterImageProcessor).should().process(event);
    }
}
