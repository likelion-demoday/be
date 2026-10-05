package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.RecordingUploadResponseDto;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.audio.AudioDurationReader;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecordingService {

    // 30분 wav는 약 300MB로 용량 제한을 넘으므로 압축 포맷만 받는다
    private static final List<String> ALLOWED_EXTENSIONS = List.of("mp3", "m4a");
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final int MIN_DURATION_SECONDS = 5 * 60;
    private static final int MAX_DURATION_SECONDS = 30 * 60;
    public static final Set<RecordingStatus> UNPAID_STATUSES =
            EnumSet.of(RecordingStatus.UPLOADED, RecordingStatus.TYPE_SELECTED);

    private final RecordingRepository recordingRepository;
    private final LocalFileStorage localFileStorage;
    private final AudioDurationReader audioDurationReader;

    @Transactional
    public RecordingUploadResponseDto upload(Long userId, MultipartFile audioFile) {
        String extension = validateExtension(audioFile);
        validateSize(audioFile);

        String filePath = localFileStorage.save(audioFile, extension);
        try {
            int durationSeconds = validateDuration(filePath);

            Recording recording = Recording.create(userId, filePath, durationSeconds);
            recordingRepository.save(recording);

            return new RecordingUploadResponseDto(recording.getId());
        } catch (RuntimeException e) {
            // DB에 기록이 없으면 보관기간 삭제 스케줄러도 찾지 못하므로 여기서 지운다
            localFileStorage.delete(filePath);
            throw e;
        }
    }

    private String validateExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return extension;
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new GeneralException(RecordingErrorCode.FILE_TOO_LARGE);
        }
    }

    // 프론트가 "너무 짧음"과 "너무 김"을 구분해 안내할 수 있도록 서로 다른 코드로 응답한다
    private int validateDuration(String filePath) {
        int durationSeconds = audioDurationReader.readSeconds(filePath);
        if (durationSeconds < MIN_DURATION_SECONDS) {
            throw new GeneralException(RecordingErrorCode.DURATION_TOO_SHORT);
        }
        if (durationSeconds > MAX_DURATION_SECONDS) {
            throw new GeneralException(RecordingErrorCode.DURATION_TOO_LONG);
        }
        return durationSeconds;
    }

    @Transactional
    public void selectType(Long recordingId, Long userId, RelationshipType relationshipType) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));

        recording.selectType(relationshipType);
    }

    // 외부 전사 요청 전에 상태를 먼저 바꿔 같은 녹음이 중복 요청되지 않게 한다
    @Transactional
    public String startTranscribing(Long recordingId) {
        Recording recording = findRecording(recordingId);
        recording.startTranscribing();
        return recording.getAudioFilePath();
    }

    @Transactional
    public void fail(Long recordingId) {
        findRecording(recordingId).fail();
    }

    // 음성 파일만 지우고 녹음·보고서·전사 텍스트는 남긴다 (보관 기간 만료, 대화 삭제에서 사용)
    // 파일 삭제에 실패하면 기록하지 않아 다음 실행에서 다시 시도된다
    @Transactional
    public boolean deleteAudio(Long recordingId) {
        Recording recording = findRecording(recordingId);
        if (!recording.hasAudio()) {
            return true;
        }
        if (!localFileStorage.delete(recording.getAudioFilePath())) {
            return false;
        }
        recording.markAudioDeleted();
        return true;
    }

    // 결제 전에 이탈한 녹음은 남길 정보가 없어 파일과 녹음 기록을 모두 지운다
    @Transactional
    public boolean deleteAbandoned(Long recordingId) {
        Recording recording = findRecording(recordingId);
        // 조회 이후 결제가 진행됐다면 지우지 않는다
        if (!UNPAID_STATUSES.contains(recording.getStatus())) {
            return false;
        }
        if (!localFileStorage.delete(recording.getAudioFilePath())) {
            return false;
        }
        recordingRepository.delete(recording);
        return true;
    }

    private Recording findRecording(Long recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
    }
}
