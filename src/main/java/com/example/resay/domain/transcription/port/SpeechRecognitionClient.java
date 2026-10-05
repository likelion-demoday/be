package com.example.resay.domain.transcription.port;

import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.model.RecognitionResult;

// 음성 파일을 외부 전사 서비스에 보내고 결과를 받는 경계
public interface SpeechRecognitionClient {

    TranscriptionProvider provider();

    // callbackSecret은 결과를 돌려받을 callback 주소에 포함된다
    String requestRecognition(String audioFilePath, String callbackSecret);

    // callback으로 받은 원본 응답을 도메인 결과로 바꾼다
    RecognitionResult parseResult(String rawBody);
}
