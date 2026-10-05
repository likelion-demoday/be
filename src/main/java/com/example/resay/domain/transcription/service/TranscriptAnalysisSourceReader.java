package com.example.resay.domain.transcription.service;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.AnalysisSpeaker;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import com.example.resay.domain.recording.entity.ParentChildRole;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 저장된 녹음·전사 구간·화자 지정 정보를 분석 입력으로 조립한다
// (mock 프로필에서는 목업 분석 입력을 사용한다)
@Profile("!mock")
@Component
@RequiredArgsConstructor
public class TranscriptAnalysisSourceReader implements AnalysisSourceReader {

    private final RecordingRepository recordingRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AnalysisSource read(Long recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음을 찾을 수 없습니다. recordingId=" + recordingId));
        if (recording.getSelfSpeakerLabel() == null || recording.getRelationshipType() == null) {
            throw new IllegalStateException("화자가 지정되지 않은 녹음입니다. recordingId=" + recordingId);
        }
        String selfName = userRepository.findById(recording.getUserId())
                .map(User::getNickname)
                .orElseThrow(() -> new IllegalStateException("녹음 소유자를 찾을 수 없습니다. recordingId=" + recordingId));

        SpeakerRole selfRole = selfRole(recording);
        SpeakerRole partnerRole = partnerRole(recording.getRelationshipType(), selfRole);

        List<AnalysisSegment> segments = transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recordingId)
                .stream()
                .map(segment -> toAnalysisSegment(segment, recording.getSelfSpeakerLabel(), selfRole, partnerRole))
                .toList();

        return new AnalysisSource(
                recordingId,
                AnalysisScenario.valueOf(recording.getRelationshipType().name()),
                recording.getDurationSeconds() * 1000L,
                List.of(
                        new AnalysisSpeaker(selfRole, selfName),
                        new AnalysisSpeaker(partnerRole, recording.getPartnerNickname())
                ),
                segments
        );
    }

    private SpeakerRole selfRole(Recording recording) {
        if (recording.getRelationshipType() == RelationshipType.PARENT_CHILD_CONFLICT) {
            return recording.getParentChildRole() == ParentChildRole.PARENT ? SpeakerRole.PARENT : SpeakerRole.CHILD;
        }
        return SpeakerRole.SELF;
    }

    // 본인을 뺀 나머지 화자의 역할은 관계유형으로 정해진다
    private SpeakerRole partnerRole(RelationshipType relationshipType, SpeakerRole selfRole) {
        return switch (relationshipType) {
            case FRIEND_DAILY -> SpeakerRole.FRIEND;
            case COUPLE_DAILY, COUPLE_CONFLICT -> SpeakerRole.PARTNER;
            case PARENT_CHILD_CONFLICT -> selfRole == SpeakerRole.PARENT ? SpeakerRole.CHILD : SpeakerRole.PARENT;
        };
    }

    private AnalysisSegment toAnalysisSegment(TranscriptSegment segment, String selfLabel,
                                              SpeakerRole selfRole, SpeakerRole partnerRole) {
        SpeakerRole role = segment.getSpeakerLabel().equals(selfLabel) ? selfRole : partnerRole;
        return new AnalysisSegment(segment.getId(), role, segment.getStartMs(), segment.getEndMs(), segment.getContent());
    }
}
