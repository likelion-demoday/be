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
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 화자 선택 화면에서 "본인 목소리"를 고를 수 있도록 화자별로 들려줄 구간을 고른다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakerSampleService {

    // 화면에 화자마다 두 개씩 보여준다
    static final int SAMPLES_PER_SPEAKER = 2;
    // 목소리를 알아듣기 쉬우면서 짧은 길이 (문장 두 개 정도)
    static final long PREFERRED_MIN_MS = 5_000;
    static final long PREFERRED_MAX_MS = 7_000;
    // 이보다 긴 발화는 통째로 들려주기엔 길어서 앞부분만 자른다
    static final long WHOLE_MAX_MS = 10_000;

    private final RecordingRepository recordingRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

    public List<SpeakerSample> getSamples(Long recordingId, Long userId) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted()) // 대화를 삭제한 녹음은 없는 것으로 본다
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

    // 문장이 끊기지 않게 발화를 통째로 고르되, 아래 순서로 화자마다 2개를 채운다
    // 1) 5~7초 발화 (긴 것부터)  2) 7~10초 발화 (7초에 가까운 것부터)
    // 3) 10초 넘는 발화의 앞 7초  4) 5초 미만 발화 (긴 것부터, 거의 말하지 않은 화자용)
    // 고른 샘플은 녹음 순서로 정렬한다
    static List<SampleRange> pickSamples(List<TranscriptSegment> segments) {
        Comparator<TranscriptSegment> longestFirst = Comparator.comparingLong(SpeakerSampleService::lengthMs).reversed();
        Comparator<TranscriptSegment> shortestFirst = Comparator.comparingLong(SpeakerSampleService::lengthMs);

        return Stream.of(
                        select(segments, length -> length >= PREFERRED_MIN_MS && length <= PREFERRED_MAX_MS, longestFirst),
                        select(segments, length -> length > PREFERRED_MAX_MS && length <= WHOLE_MAX_MS, shortestFirst),
                        select(segments, length -> length > WHOLE_MAX_MS, longestFirst),
                        select(segments, length -> length < PREFERRED_MIN_MS, longestFirst))
                .flatMap(List::stream)
                .limit(SAMPLES_PER_SPEAKER)
                .map(SpeakerSampleService::toSampleRange)
                .sorted(Comparator.comparingLong(SampleRange::startMs))
                .toList();
    }

    private static List<TranscriptSegment> select(List<TranscriptSegment> segments, LongPredicate length,
                                                  Comparator<TranscriptSegment> order) {
        return segments.stream().filter(segment -> length.test(lengthMs(segment))).sorted(order).toList();
    }

    private static SampleRange toSampleRange(TranscriptSegment segment) {
        long endMs = lengthMs(segment) > WHOLE_MAX_MS ? segment.getStartMs() + PREFERRED_MAX_MS : segment.getEndMs();
        return new SampleRange(segment.getStartMs(), endMs);
    }

    private static long lengthMs(TranscriptSegment segment) {
        return segment.getEndMs() - segment.getStartMs();
    }
}
