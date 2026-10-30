package com.example.resay.domain.character.service;

import com.example.resay.domain.credit.config.CreditProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CharacterImageStallReconcilerTest {

    @Mock
    private CharacterImageService characterImageService;

    @Test
    void usesSameTimeoutAsCreditRefundReconciler() {
        CreditProperties properties = new CreditProperties(
                new CreditProperties.Price(1500, 2000, 500),
                Duration.ofMinutes(150)
        );
        CharacterImageStallReconciler reconciler =
                new CharacterImageStallReconciler(characterImageService, properties);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime earliest = LocalDateTime.now().minusMinutes(150);

        reconciler.reconcile();

        then(characterImageService).should().failStalled(cutoff.capture());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(earliest)
                .isBeforeOrEqualTo(LocalDateTime.now().minusMinutes(150));
    }
}
