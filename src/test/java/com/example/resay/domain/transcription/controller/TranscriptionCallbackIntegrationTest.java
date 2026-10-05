package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.entity.TranscriptionStatus;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TranscriptionCallbackIntegrationTest {

    private static final String CALLBACK_URL = "/api/v1/transcriptions/callback/";

    // 실연동에서 확인한 CLOVA callback 형식 (대화 내용은 예시로 바꿈)
    private static final String COMPLETED_BODY = """
            {"result":"COMPLETED","message":"Succeeded","token":"job-token","progress":100,
             "segments":[
               {"start":4510,"end":8100,"text":"안녕하세요. 무엇을 도와드릴까요?","confidence":0.99,"speaker":{"label":"2","name":"B","edited":false}},
               {"start":350,"end":2420,"text":"안녕","confidence":0.99,"speaker":{"label":"1","name":"A","edited":false}}
             ],
             "speakers":[{"label":"1","name":"A"},{"label":"2","name":"B"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    private Recording recording;
    private Transcription transcription;

    @BeforeEach
    void setUp() {
        recording = Recording.create(1L, "callback-test.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recordingRepository.saveAndFlush(recording);

        transcription = Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH);
        transcription.assignJobToken("job-token");
        transcriptionRepository.saveAndFlush(transcription);
    }

    @Test
    void 로그인_없이_callback을_받아_화자별_구간을_저장한다() throws Exception {
        mockMvc.perform(post(CALLBACK_URL + transcription.getCallbackSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION200_1"));

        List<TranscriptSegment> segments = transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId());
        assertThat(segments).extracting(TranscriptSegment::getSpeakerLabel).containsExactly("1", "2");
        assertThat(segments).extracting(TranscriptSegment::getContent)
                .containsExactly("안녕", "안녕하세요. 무엇을 도와드릴까요?");
        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
    }

    @Test
    void 화자가_한_명이면_녹음을_화자_구분_실패로_바꾼다() throws Exception {
        String oneSpeaker = COMPLETED_BODY.replace("\"label\":\"2\"", "\"label\":\"1\"");

        mockMvc.perform(post(CALLBACK_URL + transcription.getCallbackSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneSpeaker))
                .andExpect(status().isOk());

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recording.getFailureReason()).isEqualTo(RecordingFailureReason.SPEAKER_NOT_SEPARATED);
        assertThat(transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId())).isEmpty();
    }

    @Test
    void 모르는_비밀값이면_404() throws Exception {
        mockMvc.perform(post(CALLBACK_URL + "unknown-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETED_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION404_1"));
    }

    @Test
    void 형식이_잘못된_본문이면_400() throws Exception {
        mockMvc.perform(post(CALLBACK_URL + transcription.getCallbackSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION400_1"));
    }
}
