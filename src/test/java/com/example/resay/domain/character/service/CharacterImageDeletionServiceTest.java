package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.event.CharacterImagesDeletedEvent;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CharacterImageDeletionServiceTest {

    @Mock
    private CharacterImageRepository characterImageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void deletesMetadataAndPublishesStoredObjectKeys() {
        CharacterImage completed = completedImage();
        CharacterImage failed = failedImage();
        given(characterImageRepository.findAllByAnalysisId(10L))
                .willReturn(List.of(completed, failed));
        CharacterImageService service = service();

        service.deleteAllByAnalysisId(10L);

        then(characterImageRepository).should().deleteAll(List.of(completed, failed));
        ArgumentCaptor<CharacterImagesDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(CharacterImagesDeletedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().objectKeys()).containsExactly("10/self.png");
    }

    @Test
    void rejectsGeneratingImageWithoutDeletingMetadata() {
        CharacterImage generating = CharacterImage.prepare(10L, SpeakerRole.SELF);
        generating.start();
        given(characterImageRepository.findAllByAnalysisId(10L))
                .willReturn(List.of(generating));
        CharacterImageService service = service();

        assertThatThrownBy(() -> service.deleteAllByAnalysisId(10L))
                .isInstanceOf(CharacterImageDeletionInProgressException.class);

        then(characterImageRepository).should(never()).deleteAll(List.of(generating));
        then(eventPublisher).shouldHaveNoInteractions();
    }

    private CharacterImageService service() {
        return new CharacterImageService(characterImageRepository, eventPublisher);
    }

    private CharacterImage completedImage() {
        CharacterImage image = CharacterImage.prepare(10L, SpeakerRole.SELF);
        image.start();
        image.complete("10/self.png", "image/png", "test-model", "test-v1");
        return image;
    }

    private CharacterImage failedImage() {
        CharacterImage image = CharacterImage.prepare(10L, SpeakerRole.FRIEND);
        image.start();
        image.fail("test-failure");
        return image;
    }
}
