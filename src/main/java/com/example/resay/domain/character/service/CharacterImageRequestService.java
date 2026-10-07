package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.code.CharacterImageErrorCode;
import com.example.resay.domain.character.dto.CharacterImageRequestResponseDto;
import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CharacterImageRequestService {

    private final RecordingRepository recordingRepository;
    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CharacterImageService characterImageService;
    private final CreditService creditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public CharacterImageRequestResponseDto request(Long userId, Long recordingId) {
        var recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));

        ConversationAnalysis analysis = conversationAnalysisRepository
                .findByRecordingIdForUpdate(recording.getId())
                .orElseThrow(() -> new GeneralException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new GeneralException(CharacterImageErrorCode.INVALID_ANALYSIS_STATUS);
        }

        AnalysisResult result = analysisResultRepository.findByAnalysisId(analysis.getId())
                .orElseThrow(() -> new IllegalStateException("완료된 분석의 보고서가 존재하지 않습니다."));
        List<SpeakerRole> speakerRoles = characterSpeakerRoles(readReport(result.getResultJson()));
        boolean prepared = characterImageService.prepare(analysis.getId(), speakerRoles);
        if (!prepared) {
            return new CharacterImageRequestResponseDto(recordingId, true);
        }

        creditService.useForCharacter(userId, recordingId);
        eventPublisher.publishEvent(new CharacterImageRequestedEvent(
                analysis.getId(),
                recordingId
        ));
        return new CharacterImageRequestResponseDto(recordingId, false);
    }

    private AnalysisReport readReport(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AnalysisReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 분석 보고서를 읽을 수 없습니다.", exception);
        }
    }

    private List<SpeakerRole> characterSpeakerRoles(AnalysisReport report) {
        if (report.qualitativeAnalysis() == null
                || report.qualitativeAnalysis().characterInsights() == null) {
            throw new GeneralException(CharacterImageErrorCode.CHARACTER_INSIGHT_NOT_FOUND);
        }
        List<SpeakerRole> speakerRoles = report.qualitativeAnalysis().characterInsights().stream()
                .map(insight -> insight.speakerRole())
                .distinct()
                .toList();
        if (speakerRoles.isEmpty()) {
            throw new GeneralException(CharacterImageErrorCode.CHARACTER_INSIGHT_NOT_FOUND);
        }
        return speakerRoles;
    }
}
