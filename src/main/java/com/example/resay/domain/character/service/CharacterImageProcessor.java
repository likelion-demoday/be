package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageGenerator;
import com.example.resay.domain.character.port.CharacterImageStorage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterImageProcessor {

    private static final int FAILURE_CODE_MAX_LENGTH = 100;

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CharacterImageService characterImageService;
    private final CharacterImageGenerator characterImageGenerator;
    private final CharacterImageStorage characterImageStorage;
    private final ObjectMapper objectMapper;

    public void process(CharacterImageRequestedEvent event) {
        ConversationAnalysis analysis = conversationAnalysisRepository.findById(event.analysisId())
                .orElseThrow(() -> new IllegalStateException("완료된 분석이 존재하지 않습니다."));
        if (!event.recordingId().equals(analysis.getRecordingId())
                || analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new IllegalStateException("캐릭터 이미지를 생성할 수 없는 분석 상태입니다.");
        }

        AnalysisResult result = analysisResultRepository.findByAnalysisId(analysis.getId())
                .orElseThrow(() -> new IllegalStateException("완료된 분석 보고서가 존재하지 않습니다."));
        AnalysisReport report = readReport(result.getResultJson());
        requireMatchingRecording(event.recordingId(), report);

        List<QualitativeAnalysis.CharacterInsight> insights = report.qualitativeAnalysis()
                .characterInsights();
        for (QualitativeAnalysis.CharacterInsight insight : insights) {
            processOne(analysis.getId(), report, insight);
        }
    }

    private void processOne(
            Long analysisId,
            AnalysisReport report,
            QualitativeAnalysis.CharacterInsight insight
    ) {
        String objectKey = null;
        boolean started = false;
        try {
            if (!characterImageService.begin(analysisId, insight.speakerRole())) {
                return;
            }
            started = true;
            GeneratedCharacterImage generatedImage = characterImageGenerator.generate(
                    new CharacterImageGenerationCommand(
                            report.recordingInfo().scenario(),
                            insight.speakerRole(),
                            insight.name(),
                            insight.description()
                    )
            );
            objectKey = characterImageStorage.save(
                    analysisId,
                    insight.speakerRole(),
                    generatedImage
            );
            characterImageService.complete(
                    analysisId,
                    insight.speakerRole(),
                    objectKey,
                    generatedImage
            );
        } catch (RuntimeException exception) {
            if (objectKey != null) {
                characterImageStorage.delete(objectKey);
            }
            if (started) {
                markFailed(analysisId, insight, exception);
            } else {
                log.error(
                        "캐릭터 이미지 작업 시작 실패: analysisId={}, speakerRole={}",
                        analysisId,
                        insight.speakerRole(),
                        exception
                );
            }
        }
    }

    private void markFailed(
            Long analysisId,
            QualitativeAnalysis.CharacterInsight insight,
            RuntimeException originalException
    ) {
        try {
            characterImageService.fail(
                    analysisId,
                    insight.speakerRole(),
                    failureCode(originalException)
            );
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
        }
        log.error(
                "캐릭터 이미지 생성 실패: analysisId={}, speakerRole={}",
                analysisId,
                insight.speakerRole(),
                originalException
        );
    }

    private AnalysisReport readReport(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AnalysisReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 분석 보고서를 읽을 수 없습니다.", exception);
        }
    }

    private void requireMatchingRecording(Long recordingId, AnalysisReport report) {
        if (report.recordingInfo() == null
                || !recordingId.equals(report.recordingInfo().recordingId())) {
            throw new IllegalStateException("분석과 보고서의 녹음이 일치하지 않습니다.");
        }
        if (report.qualitativeAnalysis() == null
                || report.qualitativeAnalysis().characterInsights() == null) {
            throw new IllegalStateException("캐릭터 분석 결과가 존재하지 않습니다.");
        }
    }

    private String failureCode(RuntimeException exception) {
        String code = exception.getClass().getSimpleName();
        if (code.length() <= FAILURE_CODE_MAX_LENGTH) {
            return code;
        }
        return code.substring(0, FAILURE_CODE_MAX_LENGTH);
    }
}
