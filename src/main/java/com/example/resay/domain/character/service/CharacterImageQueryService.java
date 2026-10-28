package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.CharacterInsightTextPolicy;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.code.CharacterImageErrorCode;
import com.example.resay.domain.character.dto.CharacterImageListResponseDto;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.model.CharacterCardStyle;
import com.example.resay.domain.character.port.CharacterImageStorage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterImageQueryService {

    private final RecordingRepository recordingRepository;
    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CharacterImageRepository characterImageRepository;
    private final CharacterImageStorage characterImageStorage;
    private final CharacterCardStyleResolver characterCardStyleResolver;
    private final ObjectMapper objectMapper;

    public CharacterImageListResponseDto getAll(Long userId, Long recordingId) {
        ConversationAnalysis analysis = findOwnedAnalysis(userId, recordingId);
        var characterImages = characterImageRepository.findAllByAnalysisId(analysis.getId());
        if (characterImages.isEmpty()) {
            return new CharacterImageListResponseDto(recordingId, List.of());
        }

        Map<SpeakerRole, QualitativeAnalysis.CharacterInsight> insightsByRole =
                readCharacterInsights(analysis.getId());
        var images = characterImages.stream()
                .sorted(Comparator.comparing(image -> image.getSpeakerRole().name()))
                .map(image -> toItem(recordingId, image, insightsByRole))
                .toList();
        return new CharacterImageListResponseDto(recordingId, images);
    }

    private CharacterImageListResponseDto.CharacterImageItem toItem(
            Long recordingId,
            CharacterImage image,
            Map<SpeakerRole, QualitativeAnalysis.CharacterInsight> insightsByRole
    ) {
        QualitativeAnalysis.CharacterInsight insight = insightsByRole.get(image.getSpeakerRole());
        if (insight == null) {
            throw new IllegalStateException("캐릭터 이미지에 대응하는 분석 결과가 없습니다.");
        }
        CharacterCardStyle style = characterCardStyleResolver.resolve(
                image.getSpeakerRole(),
                insight.name()
        );
        return new CharacterImageListResponseDto.CharacterImageItem(
                image.getSpeakerRole(),
                image.getStatus(),
                contentUrl(recordingId, image),
                CharacterInsightTextPolicy.fitName(insight.name()),
                CharacterInsightTextPolicy.fitDescription(insight.description()),
                style.characterNameTextColor(),
                style.cardBackgroundGradientColors()
        );
    }

    public CharacterImageContent getContent(
            Long userId,
            Long recordingId,
            SpeakerRole speakerRole
    ) {
        ConversationAnalysis analysis = findOwnedAnalysis(userId, recordingId);
        CharacterImage image = characterImageRepository
                .findByAnalysisIdAndSpeakerRole(analysis.getId(), speakerRole)
                .orElseThrow(() -> new GeneralException(
                        CharacterImageErrorCode.CHARACTER_IMAGE_NOT_FOUND
                ));
        if (image.getStatus() != CharacterImageStatus.COMPLETED) {
            throw new GeneralException(CharacterImageErrorCode.CHARACTER_IMAGE_NOT_READY);
        }
        return new CharacterImageContent(
                characterImageStorage.load(image.getObjectKey()),
                image.getMediaType()
        );
    }

    private ConversationAnalysis findOwnedAnalysis(Long userId, Long recordingId) {
        recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(recording -> !recording.isDeleted())
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        return conversationAnalysisRepository.findByRecordingId(recordingId)
                .orElseThrow(() -> new GeneralException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
    }

    private Map<SpeakerRole, QualitativeAnalysis.CharacterInsight> readCharacterInsights(
            Long analysisId
    ) {
        AnalysisResult result = analysisResultRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new IllegalStateException("완료된 분석 보고서가 존재하지 않습니다."));
        try {
            AnalysisReport report = objectMapper.readValue(
                    result.getResultJson(),
                    AnalysisReport.class
            );
            return report.qualitativeAnalysis().characterInsights().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            QualitativeAnalysis.CharacterInsight::speakerRole,
                            Function.identity()
                    ));
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 분석 보고서를 읽을 수 없습니다.", exception);
        }
    }

    private String contentUrl(Long recordingId, CharacterImage image) {
        if (image.getStatus() != CharacterImageStatus.COMPLETED) {
            return null;
        }
        return "/api/v1/analyses/%d/character-images/%s/content"
                .formatted(recordingId, image.getSpeakerRole().name());
    }

    public record CharacterImageContent(byte[] content, String mediaType) {

        public CharacterImageContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
