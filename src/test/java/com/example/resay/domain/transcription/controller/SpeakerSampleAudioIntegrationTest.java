package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpeakerSampleAudioIntegrationTest {

    private static final byte[] AUDIO = "0123456789abcdefghij".getBytes(); // 실제 재생 대신 바이트 단위로 확인

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private Recording recording;

    @BeforeEach
    void setUp() throws Exception {
        Path audioFile = Files.write(tempDir.resolve("sample.m4a"), AUDIO);
        user = userRepository.save(User.createLocal("sample@example.com", "encoded", "호준"));
        recording = Recording.create(user.getId(), audioFile.toString(), 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recordingRepository.save(recording);
    }

    @Test
    void 화자별_샘플_구간과_재생_주소를_주고_그_주소로_구간_재생할_수_있다() throws Exception {
        transcribed();

        String body = mockMvc.perform(get("/api/v1/recordings/{id}/speaker-samples", recording.getId())
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_4"))
                .andExpect(jsonPath("$.result.speakers[0].speakerLabel").value("SPEAKER_1"))
                .andExpect(jsonPath("$.result.speakers[0].samples.length()").value(1))
                .andExpect(jsonPath("$.result.speakers[1].speakerLabel").value("SPEAKER_2"))
                .andExpect(jsonPath("$.result.speakers[1].samples[0].startMs").value(4800))
                .andReturn().getResponse().getContentAsString();
        URI audioUrl = URI.create(JsonPath.read(body, "$.result.audioUrl"));
        String pathAndQuery = audioUrl.getRawPath() + "?" + audioUrl.getRawQuery();

        // 로그인 헤더 없이 재생 주소만으로 전체 재생
        mockMvc.perform(get(pathAndQuery))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mp4"))
                .andExpect(content().bytes(AUDIO));

        // 샘플 구간으로 건너뛰기 위한 부분 요청
        mockMvc.perform(get(pathAndQuery).header(HttpHeaders.RANGE, "bytes=10-14"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-14/20"))
                .andExpect(content().bytes("abcde".getBytes()));
    }

    @Test
    void 서명이_틀리거나_없는_재생_주소는_403() throws Exception {
        mockMvc.perform(get("/api/v1/recordings/{id}/audio", recording.getId())
                        .param("expires", String.valueOf(Long.MAX_VALUE))
                        .param("signature", "forged"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORDING403_1"));
    }

    @Test
    void 음성이_삭제됐으면_샘플_조회는_410() throws Exception {
        transcribed();
        recording.markAudioDeleted();

        mockMvc.perform(get("/api/v1/recordings/{id}/speaker-samples", recording.getId())
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("RECORDING410_1"));
    }

    @Test
    void 전사가_끝나지_않았으면_409() throws Exception {
        transcriptionRepository.save(Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH));

        mockMvc.perform(get("/api/v1/recordings/{id}/speaker-samples", recording.getId())
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORDING409_1"));
    }

    @Test
    void 다른_사람의_녹음이면_404() throws Exception {
        transcribed();
        User other = userRepository.save(User.createLocal("sample-other@example.com", "encoded", "남"));

        mockMvc.perform(get("/api/v1/recordings/{id}/speaker-samples", recording.getId())
                        .header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isNotFound());
    }

    private void transcribed() {
        Transcription transcription = Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH);
        transcription.complete();
        transcriptionRepository.save(transcription);
        transcriptSegmentRepository.saveAll(List.of(
                TranscriptSegment.create(recording.getId(), 1, "1", 1200, 4600, "오늘 하루는 어땠어?"),
                TranscriptSegment.create(recording.getId(), 2, "2", 4800, 7200, "오늘은 그냥 평범했어.")
        ));
    }

    private String token(User requester) {
        return jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
    }
}
