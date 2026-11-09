package com.example.resay.domain.share.service;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.service.CharacterImageQueryService;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.share.code.ShareErrorCode;
import com.example.resay.domain.share.config.ShareProperties;
import com.example.resay.domain.share.dto.ShareLinkResponseDto;
import com.example.resay.domain.share.dto.SharedAnalysisResponseDto;
import com.example.resay.domain.share.entity.AnalysisShare;
import com.example.resay.domain.share.repository.AnalysisShareRepository;
import com.example.resay.global.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class SharedAnalysisService {

    private final RecordingRepository recordingRepository;
    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisShareRepository analysisShareRepository;
    private final CharacterImageQueryService characterImageQueryService;
    private final ShareTokenCodec shareTokenCodec;
    private final ShareProperties shareProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SharedAnalysisService(
            RecordingRepository recordingRepository,
            ConversationAnalysisRepository conversationAnalysisRepository,
            AnalysisResultRepository analysisResultRepository,
            AnalysisShareRepository analysisShareRepository,
            CharacterImageQueryService characterImageQueryService,
            ShareTokenCodec shareTokenCodec,
            ShareProperties shareProperties,
            ObjectMapper objectMapper
    ) {
        this(
                recordingRepository,
                conversationAnalysisRepository,
                analysisResultRepository,
                analysisShareRepository,
                characterImageQueryService,
                shareTokenCodec,
                shareProperties,
                objectMapper,
                Clock.systemDefaultZone()
        );
    }

    SharedAnalysisService(
            RecordingRepository recordingRepository,
            ConversationAnalysisRepository conversationAnalysisRepository,
            AnalysisResultRepository analysisResultRepository,
            AnalysisShareRepository analysisShareRepository,
            CharacterImageQueryService characterImageQueryService,
            ShareTokenCodec shareTokenCodec,
            ShareProperties shareProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.recordingRepository = recordingRepository;
        this.conversationAnalysisRepository = conversationAnalysisRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.analysisShareRepository = analysisShareRepository;
        this.characterImageQueryService = characterImageQueryService;
        this.shareTokenCodec = shareTokenCodec;
        this.shareProperties = shareProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ShareLinkResponseDto issue(Long recordingId, Long userId) {
        Recording recording = recordingRepository
                .findByIdAndUserIdForUpdate(recordingId, userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new GeneralException(
                        RecordingErrorCode.RECORDING_NOT_FOUND
                ));
        ConversationAnalysis analysis = requireCompletedAnalysis(recording.getId());

        String token = shareTokenCodec.generate();
        String tokenHash = shareTokenCodec.hash(token);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(shareProperties.validity());

        AnalysisShare share = analysisShareRepository.findByRecordingId(recordingId)
                .orElseGet(() -> AnalysisShare.create(
                        recordingId,
                        analysis.getId(),
                        tokenHash,
                        expiresAt
                ));
        if (share.getId() != null) {
            share.rotate(analysis.getId(), tokenHash, expiresAt);
        }
        analysisShareRepository.save(share);

        return new ShareLinkResponseDto(
                shareProperties.frontendBaseUrl() + "/share#" + token,
                expiresAt
        );
    }

    @Transactional
    public void cancel(Long recordingId, Long userId) {
        recordingRepository.findByIdAndUserIdForUpdate(recordingId, userId)
                .orElseThrow(() -> new GeneralException(
                        RecordingErrorCode.RECORDING_NOT_FOUND
                ));
        analysisShareRepository.deleteByRecordingId(recordingId);
    }

    public SharedAnalysisResponseDto getReport(String authorizationHeader) {
        SharedAccess access = resolveAccess(authorizationHeader);
        AnalysisResult result = analysisResultRepository.findByAnalysisId(access.analysisId())
                .orElseThrow(this::sharedResourceNotFound);
        return SharedAnalysisResponseDto.from(readReport(result.getResultJson()));
    }

    public CharacterImageQueryService.CharacterImageContent getCharacterImage(
            String authorizationHeader,
            SpeakerRole speakerRole
    ) {
        SharedAccess access = resolveAccess(authorizationHeader);
        try {
            return characterImageQueryService.getContentByAnalysisId(
                    access.analysisId(),
                    speakerRole
            );
        } catch (GeneralException exception) {
            throw sharedResourceNotFound();
        }
    }

    private ConversationAnalysis requireCompletedAnalysis(Long recordingId) {
        ConversationAnalysis analysis = conversationAnalysisRepository
                .findByRecordingId(recordingId)
                .filter(found -> found.getStatus() == AnalysisStatus.COMPLETED)
                .orElseThrow(() -> new GeneralException(
                        ShareErrorCode.ANALYSIS_NOT_SHAREABLE
                ));
        if (!analysisResultRepository.existsByAnalysisId(analysis.getId())) {
            throw new GeneralException(ShareErrorCode.ANALYSIS_NOT_SHAREABLE);
        }
        return analysis;
    }

    private SharedAccess resolveAccess(String authorizationHeader) {
        String tokenHash = shareTokenCodec.extractAndHash(authorizationHeader);
        if (tokenHash == null) {
            throw sharedResourceNotFound();
        }

        AnalysisShare share = analysisShareRepository.findByTokenHash(tokenHash)
                .filter(found -> !found.isExpired(LocalDateTime.now(clock)))
                .orElseThrow(this::sharedResourceNotFound);
        Recording recording = recordingRepository.findById(share.getRecordingId())
                .filter(found -> !found.isDeleted())
                .orElseThrow(this::sharedResourceNotFound);
        ConversationAnalysis analysis = conversationAnalysisRepository
                .findById(share.getAnalysisId())
                .filter(found -> found.getRecordingId().equals(recording.getId()))
                .filter(found -> found.getStatus() == AnalysisStatus.COMPLETED)
                .orElseThrow(this::sharedResourceNotFound);
        return new SharedAccess(recording.getId(), analysis.getId());
    }

    private AnalysisReport readReport(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AnalysisReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 공유 보고서를 읽을 수 없습니다.", exception);
        }
    }

    private GeneralException sharedResourceNotFound() {
        return new GeneralException(ShareErrorCode.SHARED_RESOURCE_NOT_FOUND);
    }

    private record SharedAccess(Long recordingId, Long analysisId) {
    }
}
