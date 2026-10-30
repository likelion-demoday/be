package com.example.resay.domain.character.service;

import com.example.resay.domain.character.event.CharacterImagesDeletedEvent;
import com.example.resay.domain.character.port.CharacterImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterImageDeletionListener {

    private final CharacterImageStorage characterImageStorage;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CharacterImagesDeletedEvent event) {
        for (String objectKey : event.objectKeys()) {
            try {
                characterImageStorage.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("캐릭터 이미지 파일 정리 실패: objectKey={}", objectKey, exception);
            }
        }
    }
}
