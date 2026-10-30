package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.service.CharacterImageDeletionInProgressException;
import com.example.resay.domain.character.service.CharacterImageService;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisDeletionService {

    private final RecordingRepository recordingRepository;
    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CharacterImageService characterImageService;

    @Transactional
    public void delete(Long recordingId, Long userId) {
        recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        ConversationAnalysis analysis = conversationAnalysisRepository
                .findByRecordingId(recordingId)
                .orElseThrow(() -> new GeneralException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
        if (analysis.getStatus() == AnalysisStatus.ANALYZING) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_DELETE_IN_PROGRESS);
        }

        try {
            characterImageService.deleteAllByAnalysisId(analysis.getId());
        } catch (CharacterImageDeletionInProgressException exception) {
            throw new GeneralException(AnalysisErrorCode.ANALYSIS_DELETE_IN_PROGRESS);
        }
        analysisResultRepository.findByAnalysisId(analysis.getId())
                .ifPresent(analysisResultRepository::delete);
        conversationAnalysisRepository.delete(analysis);
    }
}
