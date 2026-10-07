package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import com.example.resay.global.exception.GeneralException;

public class AnalysisProcessor {

    private final AnalysisService analysisService;
    private final AnalysisSourceReader analysisSourceReader;
    private final AnalysisModelClient analysisModelClient;
    private final ConversationMetricsCalculator metricsCalculator;
    private final AnalysisReadinessValidator readinessValidator;
    private final AnalysisReportAssembler reportAssembler;

    public AnalysisProcessor(
            AnalysisService analysisService,
            AnalysisSourceReader analysisSourceReader,
            AnalysisModelClient analysisModelClient,
            ConversationMetricsCalculator metricsCalculator,
            AnalysisReadinessValidator readinessValidator,
            AnalysisReportAssembler reportAssembler
    ) {
        this.analysisService = analysisService;
        this.analysisSourceReader = analysisSourceReader;
        this.analysisModelClient = analysisModelClient;
        this.metricsCalculator = metricsCalculator;
        this.readinessValidator = readinessValidator;
        this.reportAssembler = reportAssembler;
    }

    public void process(Long recordingId) {
        try {
            analysisService.start(recordingId);
            AnalysisSource source = analysisSourceReader.read(recordingId);
            requireMatchingRecording(recordingId, source);

            ConversationMetrics metrics = metricsCalculator.calculate(source);
            readinessValidator.validate(source, metrics);
            AnalysisModelResult qualitativeResult = analysisModelClient.analyze(source);
            AnalysisModelResult report = reportAssembler.assemble(
                    source,
                    metrics,
                    qualitativeResult
            );
            analysisService.complete(recordingId, toCommand(report));
        } catch (GeneralException exception) {
            if (exception.getErrorCode() != AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS) {
                markFailed(recordingId, exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            markFailed(recordingId, exception);
            throw exception;
        }
    }

    private void requireMatchingRecording(Long recordingId, AnalysisSource source) {
        if (source == null || !recordingId.equals(source.recordingId())) {
            throw new IllegalStateException("요청한 녹음과 분석 입력의 녹음이 일치하지 않습니다.");
        }
    }

    private AnalysisResultCommand toCommand(AnalysisModelResult result) {
        if (result == null) {
            throw new IllegalStateException("AI 분석 결과가 비어 있습니다.");
        }
        return new AnalysisResultCommand(
                result.resultJson(),
                result.modelName(),
                result.promptVersion(),
                result.schemaVersion()
        );
    }

    private void markFailed(Long recordingId, RuntimeException originalException) {
        try {
            analysisService.recordFailure(recordingId, failureReason(originalException));
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
        }
    }

    private AnalysisFailureReason failureReason(RuntimeException exception) {
        if (exception instanceof AnalysisReadinessException) {
            return AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA;
        }
        return AnalysisFailureReason.PROCESSING_ERROR;
    }
}
