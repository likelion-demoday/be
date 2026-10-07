package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.code.CharacterImageErrorCode;
import com.example.resay.domain.character.dto.CharacterImageListResponseDto;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.port.CharacterImageStorage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterImageQueryService {

    private final RecordingRepository recordingRepository;
    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final CharacterImageRepository characterImageRepository;
    private final CharacterImageStorage characterImageStorage;

    public CharacterImageListResponseDto getAll(Long userId, Long recordingId) {
        ConversationAnalysis analysis = findOwnedAnalysis(userId, recordingId);
        var images = characterImageRepository.findAllByAnalysisId(analysis.getId()).stream()
                .sorted(Comparator.comparing(image -> image.getSpeakerRole().name()))
                .map(image -> new CharacterImageListResponseDto.CharacterImageItem(
                        image.getSpeakerRole(),
                        image.getStatus(),
                        contentUrl(recordingId, image)
                ))
                .toList();
        return new CharacterImageListResponseDto(recordingId, images);
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
