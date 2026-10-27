package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
public class AnalysisFailureClassifier {

    public AnalysisFailure classify(RuntimeException exception) {
        if (exception instanceof AnalysisReadinessException) {
            return new AnalysisFailure(
                    AnalysisFailureCategory.INSUFFICIENT_INPUT,
                    AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA
            );
        }
        if (exception instanceof AnalysisProcessingException processingException) {
            return new AnalysisFailure(
                    processingException.getCategory(),
                    publicReason(processingException.getCategory())
            );
        }
        if (hasCause(exception, DataAccessException.class)
                || hasCause(exception, TransactionException.class)
                || hasCause(exception, PersistenceException.class)) {
            return new AnalysisFailure(
                    AnalysisFailureCategory.PERSISTENCE_ERROR,
                    AnalysisFailureReason.PROCESSING_ERROR
            );
        }
        return new AnalysisFailure(
                AnalysisFailureCategory.UNEXPECTED_ERROR,
                AnalysisFailureReason.PROCESSING_ERROR
        );
    }

    private AnalysisFailureReason publicReason(AnalysisFailureCategory category) {
        return switch (category) {
            case PROVIDER_RATE_LIMIT, PROVIDER_TIMEOUT, PROVIDER_NETWORK, PROVIDER_SERVER_ERROR ->
                    AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE;
            case RESPONSE_LENGTH_EXCEEDED, RESPONSE_INCOMPLETE, RESPONSE_FORMAT_ERROR,
                    RESPONSE_VALIDATION_ERROR -> AnalysisFailureReason.INVALID_ANALYSIS_RESPONSE;
            case INSUFFICIENT_INPUT -> AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA;
            case PROVIDER_CONFIGURATION, PROVIDER_AUTHENTICATION, PERSISTENCE_ERROR,
                    UNEXPECTED_ERROR -> AnalysisFailureReason.PROCESSING_ERROR;
        };
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record AnalysisFailure(
            AnalysisFailureCategory category,
            AnalysisFailureReason publicReason
    ) {
    }
}
