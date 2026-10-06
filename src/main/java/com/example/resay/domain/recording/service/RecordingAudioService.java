package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.security.AudioUrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

// 서명된 임시 주소로 원본 음성을 내려준다 (부분 요청은 스프링이 처리)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordingAudioService {

    private final RecordingRepository recordingRepository;
    private final AudioUrlSigner audioUrlSigner;

    public AudioFile getAudio(Long recordingId, long expires, String signature) {
        if (!audioUrlSigner.verify(recordingId, expires, signature)) {
            throw new GeneralException(RecordingErrorCode.INVALID_AUDIO_URL);
        }
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        Path path = Path.of(recording.getAudioFilePath());
        if (!recording.hasAudio() || !Files.isRegularFile(path)) {
            throw new GeneralException(RecordingErrorCode.AUDIO_DELETED);
        }
        return new AudioFile(new FileSystemResource(path), mediaType(path));
    }

    // 업로드 허용 포맷(m4a, mp3)에 맞춘 재생 형식
    private MediaType mediaType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp3") ? MediaType.parseMediaType("audio/mpeg") : MediaType.parseMediaType("audio/mp4");
    }

    public record AudioFile(FileSystemResource resource, MediaType mediaType) {
    }
}
