package com.example.resay.global.infrastructure.clova;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ClovaSpeechResponseTest {

    // #22에서 실제 CLOVA Speech 응답으로 확인한 형식 (분석에 쓰지 않는 필드는 줄임)
    private static final String SAMPLE_JSON = """
            {"result":"COMPLETED","message":"Succeeded","token":"sample-token","progress":100,
             "segments":[
               {"start":350,"end":2420,"text":"안녕","confidence":0.9997,"diarization":{"label":"1"},"speaker":{"label":"1","name":"A","edited":false},"words":[[1040,1330,"안녕"]]},
               {"start":4510,"end":8100,"text":"안녕하세요. 무엇을 도와드릴까요?","confidence":0.9997,"diarization":{"label":"2"},"speaker":{"label":"2","name":"B","edited":false},"words":[]}
             ],
             "text":"안녕 안녕하세요. 무엇을 도와드릴까요?",
             "speakers":[{"label":"1","name":"A","edited":false},{"label":"2","name":"B","edited":false}],
             "events":[],"eventTypes":[]}
            """;

    @Test
    void 전사_완료_응답을_화자별_구간으로_파싱() throws Exception {
        ClovaSpeechResponse response = new ObjectMapper().readValue(SAMPLE_JSON, ClovaSpeechResponse.class);

        assertThat(response.result()).isEqualTo("COMPLETED");
        assertThat(response.speakers()).hasSize(2);
        assertThat(response.segments()).hasSize(2);
        assertThat(response.segments().get(1).speaker().label()).isEqualTo("2");
        assertThat(response.segments().get(1).start()).isEqualTo(4510);
        assertThat(response.segments().get(1).text()).isEqualTo("안녕하세요. 무엇을 도와드릴까요?");
    }
}
