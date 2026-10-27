package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisFailureClassifierTest {

    private final AnalysisFailureClassifier classifier = new AnalysisFailureClassifier();

    @Test
    void exposesOnlySafeReasonForProviderAuthenticationFailure() {
        var failure = classifier.classify(new AnalysisProcessingException(
                AnalysisFailureCategory.PROVIDER_AUTHENTICATION,
                "provider authentication failed",
                null
        ));

        assertThat(failure.category()).isEqualTo(AnalysisFailureCategory.PROVIDER_AUTHENTICATION);
        assertThat(failure.publicReason()).isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
    }

    @Test
    void exposesTemporaryReasonForRetryableProviderFailure() {
        var failure = classifier.classify(new AnalysisProcessingException(
                AnalysisFailureCategory.PROVIDER_RATE_LIMIT,
                "rate limited",
                null
        ));

        assertThat(failure.publicReason())
                .isEqualTo(AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void exposesInvalidResponseReasonForValidationFailure() {
        var failure = classifier.classify(new AnalysisProcessingException(
                AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR,
                "invalid evidence",
                null
        ));

        assertThat(failure.publicReason()).isEqualTo(AnalysisFailureReason.INVALID_ANALYSIS_RESPONSE);
    }

    @Test
    void detectsNestedPersistenceFailure() {
        RuntimeException exception = new IllegalStateException(
                "wrapped",
                new DataAccessResourceFailureException("database unavailable")
        );

        assertThat(classifier.classify(exception).category())
                .isEqualTo(AnalysisFailureCategory.PERSISTENCE_ERROR);
    }
}
