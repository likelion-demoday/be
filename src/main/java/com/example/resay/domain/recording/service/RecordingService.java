package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.RecordingUploadResponseDto;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordingService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("mp3", "wav", "m4a");
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final int MIN_DURATION_SECONDS = 5 * 60;
    private static final int MAX_DURATION_SECONDS = 30 * 60;

    private final RecordingRepository recordingRepository;
    private final LocalFileStorage localFileStorage;

    @Transactional
    public RecordingUploadResponseDto upload(Long userId, MultipartFile audioFile) {
        String extension = validateExtension(audioFile);
        validateSize(audioFile);

        String filePath = localFileStorage.save(audioFile, extension);
        validateDuration(filePath);

        Recording recording = Recording.create(userId, filePath);
        recordingRepository.save(recording);

        return new RecordingUploadResponseDto(recording.getId());
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

    private void validateDuration(String filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            int durationSeconds = audioFile.getAudioHeader().getTrackLength();
            if (durationSeconds < MIN_DURATION_SECONDS || durationSeconds > MAX_DURATION_SECONDS) {
                Files.deleteIfExists(Paths.get(filePath));
                throw new GeneralException(RecordingErrorCode.INVALID_DURATION);
            }
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    @Transactional
    public void selectType(Long recordingId, Long userId, RelationshipType relationshipType) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));

        recording.selectType(relationshipType);
    }
}
