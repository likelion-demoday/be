package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.SpeakerSamplesResponseDto.SampleRange;
import com.example.resay.domain.recording.dto.SpeakerSamplesResponseDto.SpeakerSample;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionStatus;
import com.example.resay.domain.transcription.model.SpeakerLabels;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 화자 선택 화면에서 "본인 목소리"를 고를 수 있도록 화자별로 들려줄 구간을 고른다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakerSampleService {

    // 짧은 맞장구보다 목소리를 알아듣기 쉬운 긴 발화를 고른다
    static final int SAMPLES_PER_SPEAKER = 3;

    private final RecordingRepository recordingRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

    public List<SpeakerSample> getSamples(Long recordingId, Long userId) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        boolean transcribed = transcriptionRepository.findByRecordingId(recordingId)
                .map(Transcription::getStatus)
                .filter(status -> status == TranscriptionStatus.COMPLETED)
                .isPresent();
        if (!transcribed) {
            throw new GeneralException(RecordingErrorCode.TRANSCRIPTION_NOT_COMPLETED);
        }
        if (!recording.hasAudio()) {
            throw new GeneralException(RecordingErrorCode.AUDIO_DELETED);
        }

        Map<String, List<TranscriptSegment>> bySpeaker = transcriptSegmentRepository
                .findByRecordingIdOrderBySegmentNo(recordingId)
                .stream()
                .collect(Collectors.groupingBy(TranscriptSegment::getSpeakerLabel, LinkedHashMap::new, Collectors.toList()));

        return bySpeaker.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SpeakerSample(SpeakerLabels.toExternal(entry.getKey()), pickSamples(entry.getValue())))
                .toList();
    }

    static List<SampleRange> pickSamples(List<TranscriptSegment> segments) {
        return segments.stream()
                .sorted(Comparator.comparingLong((TranscriptSegment s) -> s.getEndMs() - s.getStartMs()).reversed())
                .limit(SAMPLES_PER_SPEAKER)
                .sorted(Comparator.comparingLong(TranscriptSegment::getStartMs))
                .map(segment -> new SampleRange(segment.getStartMs(), segment.getEndMs()))
                .toList();
    }
}
