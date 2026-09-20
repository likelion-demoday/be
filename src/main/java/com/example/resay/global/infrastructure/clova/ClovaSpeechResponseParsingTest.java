package com.example.resay.global.infrastructure.clova;

import tools.jackson.databind.ObjectMapper;

public class ClovaSpeechResponseParsingTest {

    public static void main(String[] args) throws Exception {
        String sampleJson = """
                {"result":"COMPLETED","message":"Succeeded","token":"1f1c1d8e2b854285997885e22763477a","version":"ncp_v2_v2.4.6_2-8fcb825-20260120_dev_v4.2.20.1_ko_firedepartment_20250923_","params":{"service":"ncp","domain":"general","lang":"ko","completion":"sync","diarization":{"enable":true,"speakerCountMin":-1,"speakerCountMax":-1},"sed":{"enable":false},"boostings":[],"forbiddens":"","wordAlignment":true,"fullText":true,"noiseFiltering":true,"resultToObs":false,"priority":0,"userdata":{"_ncp_DomainCode":"resay-clova","_ncp_DomainId":17629,"_ncp_TaskId":64880836,"_ncp_TraceId":"6fee7839fc2d47aba1687b5ba97ecc84"}},"progress":100,"keywords":{},"segments":[{"start":350,"end":2420,"text":"안녕","confidence":0.9997,"diarization":{"label":"1"},"speaker":{"label":"1","name":"A","edited":false},"words":[[1040,1330,"안녕"]],"textEdited":"안녕"},{"start":4510,"end":8100,"text":"안녕하세요. 무엇을 도와드릴까요?","confidence":0.9997,"diarization":{"label":"2"},"speaker":{"label":"2","name":"B","edited":false},"words":[[5140,5670,"안녕하세요."],[6340,6730,"무엇을"],[6730,7390,"도와드릴까요?"]],"textEdited":"안녕하세요. 무엇을 도와드릴까요?"},{"start":8610,"end":11230,"text":"지금 뭐 하고 있어? 너","confidence":0.9466,"diarization":{"label":"1"},"speaker":{"label":"1","name":"A","edited":false},"words":[[9220,9470,"지금"],[9540,9690,"뭐"],[9690,9890,"하고"],[9920,10150,"있어?"],[10300,10450,"너"]],"textEdited":"지금 뭐 하고 있어? 너"}],"text":"안녕 안녕하세요. 무엇을 도와드릴까요? 지금 뭐 하고 있어? 너","confidence":0.9659639,"speakers":[{"label":"1","name":"A","edited":false},{"label":"2","name":"B","edited":false}],"events":[],"eventTypes":[]}
                """;

        ObjectMapper mapper = new ObjectMapper();
        ClovaSpeechResponse response = mapper.readValue(sampleJson, ClovaSpeechResponse.class);

        System.out.println("화자 수: " + response.speakers().size());
        response.segments().forEach(seg ->
                System.out.println("[" + seg.speaker().label() + "] " + seg.text()));
    }
}
