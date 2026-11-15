package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 분석에만 필요한 원본 음성과 전체 전사문을 제거한다.
// 보고서·녹음 메타데이터는 남겨 보고서 조회, 요약, 공유, 캐릭터 생성에 사용한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingSourceCleanupService {

    private final RecordingRepository recordingRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final LocalFileStorage localFileStorage;

    // 완료·실패 상태를 저장한 트랜잭션과 분리한다. 삭제 실패가 분석 결과를 되돌리면 안 된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteSourceData(Long recordingId) {
        Recording recording = recordingRepository.findByIdForUpdate(recordingId).orElse(null);
        if (recording == null) {
            return true;
        }

        boolean audioDeleted = deleteAudio(recording);
        int deletedSegments = transcriptSegmentRepository.deleteByRecordingId(recordingId);
        if (deletedSegments > 0) {
            log.info("전사 원문 삭제: recordingId={}, segments={}", recordingId, deletedSegments);
        }
        return audioDeleted;
    }

    private boolean deleteAudio(Recording recording) {
        if (!recording.hasAudio()) {
            return true;
        }
        if (!localFileStorage.delete(recording.getAudioFilePath())) {
            return false;
        }
        recording.markAudioDeleted();
        return true;
    }
}
