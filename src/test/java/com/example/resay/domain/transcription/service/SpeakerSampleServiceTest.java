package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.dto.SpeakerSamplesResponseDto.SampleRange;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerSampleServiceTest {

    @Test
    void 가장_긴_발화_2개를_골라_시간순으로_준다() {
        List<TranscriptSegment> segments = List.of(
                TranscriptSegment.create(1L, 1, "1", 0, 1_000, "응"),            // 1초
                TranscriptSegment.create(1L, 2, "1", 2_000, 10_000, "긴 발화 1"),  // 8초
                TranscriptSegment.create(1L, 3, "1", 11_000, 12_000, "어"),      // 1초
                TranscriptSegment.create(1L, 4, "1", 20_000, 26_000, "긴 발화 2"), // 6초
                TranscriptSegment.create(1L, 5, "1", 30_000, 40_000, "긴 발화 3")  // 10초
        );

        List<SampleRange> samples = SpeakerSampleService.pickSamples(segments);

        // 가장 긴 10초·8초 발화를 녹음 순서대로
        assertThat(samples).containsExactly(
                new SampleRange(2_000, 10_000),
                new SampleRange(30_000, 40_000));
    }

    @Test
    void 발화가_2개보다_적으면_있는_만큼_준다() {
        List<TranscriptSegment> segments = List.of(TranscriptSegment.create(1L, 1, "2", 500, 3_000, "안녕"));

        assertThat(SpeakerSampleService.pickSamples(segments)).containsExactly(new SampleRange(500, 3_000));
    }
}
