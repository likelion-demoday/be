package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.code.TranscriptionErrorCode;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.model.RecognitionResult;
import com.example.resay.domain.transcription.model.RecognitionResult.RecognizedSegment;
import com.example.resay.domain.transcription.port.SpeechRecognitionClient;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    // 6분 43초 음성이 1분 안에 끝났으므로, 30분 음성도 여유 있게 기다릴 수 있는 시간
    static final Duration RESULT_TIMEOUT = Duration.ofHours(1);
    // 1:1 대화만 분석하므로 화자가 정확히 두 명이어야 한다
    private static final int REQUIRED_SPEAKER_COUNT = 2;

    private final RecordingService recordingService;
    private final SpeechRecognitionClient speechRecognitionClient;
    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

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
            recordingService.failTranscription(recordingId, RecordingFailureReason.TRANSCRIPTION_REQUEST_FAILED);
        }
    }

    // 외부 전사 서비스가 callback 주소로 보낸 결과를 저장한다
    // 주소의 비밀값이 일치하는 요청만 처리하고, 이미 처리한 결과가 다시 오면 무시한다
    @Transactional
    public void receiveResult(String callbackSecret, String rawBody) {
        Transcription transcription = transcriptionRepository.findByCallbackSecret(callbackSecret)
                .orElseThrow(() -> new GeneralException(TranscriptionErrorCode.TRANSCRIPTION_NOT_FOUND));
        Long recordingId = transcription.getRecordingId();

        if (!transcription.isRequested()) {
            log.info("이미 처리한 전사 결과입니다. recordingId={}", recordingId);
            return;
        }

        RecognitionResult result = speechRecognitionClient.parseResult(rawBody);
        verifyJobToken(transcription, result.jobToken());

        if (!result.completed()) {
            failTranscription(transcription, RecordingFailureReason.TRANSCRIPTION_FAILED);
            return;
        }

        List<RecognizedSegment> segments = usableSegments(result.segments());
        long speakerCount = segments.stream().map(RecognizedSegment::speakerLabel).distinct().count();
        if (speakerCount != REQUIRED_SPEAKER_COUNT) {
            // 두 명은 검출됐지만 발화가 부족한 경우는 분석 단계에서 판단한다
            log.info("화자를 두 명으로 구분하지 못했습니다. recordingId={}, speakerCount={}", recordingId, speakerCount);
            failTranscription(transcription, RecordingFailureReason.SPEAKER_NOT_SEPARATED);
            return;
        }

        transcriptSegmentRepository.saveAll(toEntities(recordingId, segments));
        transcription.complete();
    }

    public List<Long> findTimedOutRecordingIds(LocalDateTime now) {
        return transcriptionRepository.findTimedOutRecordingIds(now.minus(RESULT_TIMEOUT));
    }

    // 제한 시간 안에 결과가 오지 않은 녹음을 실패로 바꾼다 (이후 늦게 온 결과는 무시된다)
    @Transactional
    public void failTimedOut(Long recordingId) {
        transcriptionRepository.findByRecordingId(recordingId)
                .filter(Transcription::isRequested)
                .ifPresent(Transcription::fail);
        recordingService.failTranscription(recordingId, RecordingFailureReason.TRANSCRIPTION_TIMEOUT);
    }

    private void verifyJobToken(Transcription transcription, String jobToken) {
        if (jobToken == null || jobToken.isBlank()) {
            return;
        }
        if (transcription.getJobToken() == null) {
            // 요청 응답보다 callback이 먼저 도착한 경우
            transcription.assignJobToken(jobToken);
            return;
        }
        if (!transcription.getJobToken().equals(jobToken)) {
            log.warn("작업 토큰이 일치하지 않는 전사 결과입니다. recordingId={}", transcription.getRecordingId());
            throw new GeneralException(TranscriptionErrorCode.TRANSCRIPTION_NOT_FOUND);
        }
    }

    private void failTranscription(Transcription transcription, RecordingFailureReason reason) {
        transcription.fail();
        recordingService.failTranscription(transcription.getRecordingId(), reason);
    }

    // 내용이 없거나 시각이 잘못된 구간은 버리고 시작 시각 순서로 정렬한다
    private List<RecognizedSegment> usableSegments(List<RecognizedSegment> segments) {
        return segments.stream()
                .filter(segment -> segment.speakerLabel() != null && !segment.speakerLabel().isBlank())
                .filter(segment -> segment.text() != null && !segment.text().isBlank())
                .filter(segment -> segment.startMs() >= 0 && segment.endMs() > segment.startMs())
                .sorted(Comparator.comparingLong(RecognizedSegment::startMs))
                .toList();
    }

    private List<TranscriptSegment> toEntities(Long recordingId, List<RecognizedSegment> segments) {
        List<TranscriptSegment> entities = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            RecognizedSegment segment = segments.get(i);
            entities.add(TranscriptSegment.create(
                    recordingId, i + 1, segment.speakerLabel(), segment.startMs(), segment.endMs(), segment.text().strip()));
        }
        return entities;
    }
}
