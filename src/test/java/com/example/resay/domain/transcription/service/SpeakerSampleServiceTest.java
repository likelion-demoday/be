package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.dto.SpeakerSamplesResponseDto.SampleRange;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerSampleServiceTest {

    @Test
    void 오에서_칠초_발화_중_긴_것_2개를_통째로_골라_시간순으로_준다() {
        List<TranscriptSegment> segments = List.of(
                segment(1, 0, 1_000),        // 1초
                segment(2, 2_000, 14_000),   // 12초
                segment(3, 20_000, 26_500),  // 6.5초
                segment(4, 30_000, 33_000),  // 3초
                segment(5, 40_000, 45_000),  // 5초
                segment(6, 50_000, 58_000)   // 8초
        );

        assertThat(SpeakerSampleService.pickSamples(segments)).containsExactly(
                new SampleRange(20_000, 26_500),
                new SampleRange(40_000, 45_000));
    }

    @Test
    void 오에서_칠초_발화가_모자라면_칠에서_십초_발화를_칠초에_가까운_것부터_통째로_준다() {
        List<TranscriptSegment> segments = List.of(
                segment(1, 0, 6_000),        // 6초
                segment(2, 10_000, 19_500),  // 9.5초
                segment(3, 30_000, 37_500),  // 7.5초
                segment(4, 40_000, 55_000)   // 15초
        );

        assertThat(SpeakerSampleService.pickSamples(segments)).containsExactly(
                new SampleRange(0, 6_000),
                new SampleRange(30_000, 37_500));
    }

    @Test
    void 십초_이하_발화가_모자라면_십초_넘는_발화를_앞_칠초만_잘라_채운다() {
        List<TranscriptSegment> segments = List.of(
                segment(1, 0, 9_000),        // 9초
                segment(2, 10_000, 22_000),  // 12초 → 앞 7초
                segment(3, 30_000, 33_000)   // 3초 (긴 발화를 먼저 쓴다)
        );

        assertThat(SpeakerSampleService.pickSamples(segments)).containsExactly(
                new SampleRange(0, 9_000),
                new SampleRange(10_000, 17_000));
    }

    @Test
    void 짧은_발화밖에_없으면_있는_것_중_긴_것을_준다() {
        List<TranscriptSegment> segments = List.of(
                segment(1, 0, 500),
                segment(2, 1_000, 4_800),    // 3.8초
                segment(3, 5_000, 6_200)     // 1.2초
        );

        assertThat(SpeakerSampleService.pickSamples(segments)).containsExactly(
                new SampleRange(1_000, 4_800),
                new SampleRange(5_000, 6_200));
    }

    @Test
    void 발화가_2개보다_적으면_있는_만큼_준다() {
        assertThat(SpeakerSampleService.pickSamples(List.of(segment(1, 500, 3_000))))
                .containsExactly(new SampleRange(500, 3_000));
    }

    @Test
    void 샘플은_십초를_넘지_않는다() {
        List<TranscriptSegment> segments = List.of(segment(1, 0, 30_000), segment(2, 40_000, 60_000));

        assertThat(SpeakerSampleService.pickSamples(segments))
                .allSatisfy(sample -> assertThat(sample.endMs() - sample.startMs()).isLessThanOrEqualTo(10_000));
    }

    private TranscriptSegment segment(int segmentNo, long startMs, long endMs) {
        return TranscriptSegment.create(1L, segmentNo, "1", startMs, endMs, "발화 " + segmentNo);
    }
}
