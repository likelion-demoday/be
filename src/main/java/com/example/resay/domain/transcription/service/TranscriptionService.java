package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.port.SpeechRecognitionClient;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final RecordingService recordingService;
    private final SpeechRecognitionClient speechRecognitionClient;
    private final TranscriptionRepository transcriptionRepository;

    // 결제 완료 트랜잭션이 커밋된 뒤 호출한다
    // 음성 파일 전송이 오래 걸릴 수 있어 결제 응답을 붙잡지 않도록 별도 스레드에서 실행한다
    @Async
    public void start(Long recordingId) {
        String audioFilePath;
        try {
            audioFilePath = recordingService.startTranscribing(recordingId);
        } catch (GeneralException e) {
            log.warn("전사를 시작할 수 없는 녹음입니다. recordingId={}, code={}",
                    recordingId, e.getErrorCode().getCode());
            return;
        }

        try {
            // 짧은 음성은 토큰 저장보다 callback이 먼저 올 수 있어 callback 비밀값을 요청 전에 저장한다
            Transcription transcription = transcriptionRepository.save(
                    Transcription.prepare(recordingId, speechRecognitionClient.provider()));

            String jobToken = speechRecognitionClient.requestRecognition(
                    audioFilePath, transcription.getCallbackSecret());

            transcription.assignJobToken(jobToken);
            transcriptionRepository.save(transcription);
        } catch (RuntimeException e) {
            log.warn("전사 요청에 실패했습니다. recordingId={}", recordingId, e);
            recordingService.fail(recordingId);
        }
    }
}
