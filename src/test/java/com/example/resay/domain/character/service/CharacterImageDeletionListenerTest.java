package com.example.resay.domain.character.service;

import com.example.resay.domain.character.event.CharacterImagesDeletedEvent;
import com.example.resay.domain.character.port.CharacterImageStorage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class CharacterImageDeletionListenerTest {

    @Mock
    private CharacterImageStorage characterImageStorage;

    @Test
    void deletesEveryImageEvenWhenOneDeletionFails() {
        CharacterImageDeletionListener listener =
                new CharacterImageDeletionListener(characterImageStorage);
        willThrow(new RuntimeException("삭제 실패"))
                .given(characterImageStorage).delete("10/self.png");

        listener.handle(new CharacterImagesDeletedEvent(List.of(
                "10/self.png",
                "10/friend.png"
        )));

        then(characterImageStorage).should().delete("10/self.png");
        then(characterImageStorage).should().delete("10/friend.png");
    }
}
