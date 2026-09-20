package com.example.resay.global.infrastructure.clova;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;

import java.io.File;

public class ClovaSpeechManualTest {

    public static void main(String[] args) {
        String invokeUrl = System.getenv("CLOVA_SPEECH_INVOKE_URL");
        String secretKey = System.getenv("CLOVA_SPEECH_SECRET_KEY");

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-CLOVASPEECH-API-KEY", secretKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", new FileSystemResource(new File("C:\\Users\\User\\Downloads\\음성녹음 샘플.m4a"))); // 실제 파일 경로로 수정
        body.add("params",
                "{\"language\":\"ko-KR\",\"completion\":\"sync\",\"diarization\":{\"enable\":true},\"fullText\":true}");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 응답만 String 대신 ClovaSpeechResponse로 바로 파싱
        ResponseEntity<ClovaSpeechResponse> response = restTemplate.postForEntity(
                invokeUrl + "/recognizer/upload", requestEntity, ClovaSpeechResponse.class);

        ClovaSpeechResponse body2 = response.getBody();

        System.out.println("전체 응답: " + body2);

        System.out.println("화자 수: " + body2.speakers().size());
        body2.segments().forEach(seg ->
                System.out.println("[" + seg.speaker().label() + "] " + seg.text()));
    }
}
