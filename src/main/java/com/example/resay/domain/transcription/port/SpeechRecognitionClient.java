package com.example.resay.domain.transcription.port;

import com.example.resay.domain.transcription.entity.TranscriptionProvider;

// 음성 파일을 외부 전사 서비스에 보내고 작업 식별자를 받는 경계
public interface SpeechRecognitionClient {

    TranscriptionProvider provider();

    // callbackSecret은 결과를 돌려받을 callback 주소에 포함된다
    String requestRecognition(String audioFilePath, String callbackSecret);
}
