package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AnalysisProcessor {

    private final AnalysisService analysisService;
    private final AnalysisSourceReader analysisSourceReader;
    private final AnalysisModelClient analysisModelClient;

    public void process(Long recordingId) {
        analysisService.start(recordingId);

        try {
            AnalysisSource source = analysisSourceReader.read(recordingId);
            requireMatchingRecording(recordingId, source);
            AnalysisModelResult result = analysisModelClient.analyze(source);
            analysisService.complete(recordingId, toCommand(result));
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
            analysisService.fail(recordingId);
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
        }
    }
}
