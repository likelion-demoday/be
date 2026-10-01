package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;

    @Transactional
    public void start(Long recordingId) {
        ConversationAnalysis analysis = ConversationAnalysis.start(recordingId);
        if (conversationAnalysisRepository.existsByRecordingId(recordingId)) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS);
        }

        try {
            conversationAnalysisRepository.saveAndFlush(analysis);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void complete(Long recordingId, AnalysisResultCommand command) {
        Objects.requireNonNull(command, "분석 결과 명령은 비어 있을 수 없습니다.");
        ConversationAnalysis analysis = findAnalysis(recordingId);
        AnalysisResult result = AnalysisResult.create(
                analysis.getId(),
                command.resultJson(),
                command.modelName(),
                command.promptVersion(),
                command.schemaVersion()
        );
        if (analysisResultRepository.existsByAnalysisId(analysis.getId())) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_RESULT_ALREADY_EXISTS);
        }

        try {
            analysis.complete();
            analysisResultRepository.saveAndFlush(result);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_RESULT_ALREADY_EXISTS);
        } catch (IllegalStateException exception) {
            throw new GeneralException(AnalysisErrorCode.INVALID_ANALYSIS_STATUS);
        }
    }

    @Transactional
    public void fail(Long recordingId) {
        ConversationAnalysis analysis = findAnalysis(recordingId);
        try {
            analysis.fail();
        } catch (IllegalStateException exception) {
            throw new GeneralException(AnalysisErrorCode.INVALID_ANALYSIS_STATUS);
        }
    }

    private ConversationAnalysis findAnalysis(Long recordingId) {
        return conversationAnalysisRepository.findByRecordingId(recordingId)
                .orElseThrow(() -> new GeneralException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
    }
}
