package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.dto.AnalysisReportDto;
import com.example.resay.domain.analysis.dto.AnalysisReportResponseDto;
import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisReportQueryService {

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final RecordingRepository recordingRepository;
    private final ObjectMapper objectMapper;

    public AnalysisReportResponseDto getByRecordingId(Long recordingId, Long userId) {
        recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        return getByRecordingIdForInternalUse(recordingId);
    }

    AnalysisReportResponseDto getByRecordingIdForInternalUse(Long recordingId) {
        ConversationAnalysis analysis = conversationAnalysisRepository.findByRecordingId(recordingId)
                .orElseThrow(() -> new GeneralException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));

        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            return new AnalysisReportResponseDto(
                    recordingId,
                    analysis.getStatus(),
                    analysis.getFailureReason(),
                    null,
                    null,
                    null,
                    null
            );
        }

        AnalysisResult result = analysisResultRepository.findByAnalysisId(analysis.getId())
                .orElseThrow(() -> new IllegalStateException("완료된 분석의 보고서가 존재하지 않습니다."));
        return new AnalysisReportResponseDto(
                recordingId,
                analysis.getStatus(),
                null,
                AnalysisReportDto.from(readReport(result.getResultJson())),
                result.getModelName(),
                result.getPromptVersion(),
                result.getSchemaVersion()
        );
    }

    private AnalysisReport readReport(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AnalysisReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 분석 보고서를 읽을 수 없습니다.", exception);
        }
    }
}
