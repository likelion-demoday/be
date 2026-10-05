package com.example.resay.global.infrastructure.clova;

import com.example.resay.domain.transcription.code.TranscriptionErrorCode;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.model.RecognitionResult;
import com.example.resay.domain.transcription.model.RecognitionResult.RecognizedSegment;
import com.example.resay.domain.transcription.port.SpeechRecognitionClient;
import com.example.resay.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ClovaSpeechClient implements SpeechRecognitionClient {

    private static final String UPLOAD_PATH = "/recognizer/upload";
    private static final String API_KEY_HEADER = "X-CLOVASPEECH-API-KEY";
    private static final String LANGUAGE = "ko-KR";
    // 관계유형이 모두 1:1 대화라 화자를 2명으로 고정해 잘못 나뉘는 것을 줄인다
    private static final int SPEAKER_COUNT = 2;
    // 전사 결과 상태값 (그 외 FAILED 등은 실패로 본다)
    private static final String COMPLETED = "COMPLETED";

    private final ClovaSpeechProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ClovaSpeechClient(
            ClovaSpeechProperties properties,
            @Qualifier("clovaSpeechRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public TranscriptionProvider provider() {
        return TranscriptionProvider.CLOVA_SPEECH;
    }

    @Override
    public String requestRecognition(String audioFilePath, String callbackSecret) {
        if (!properties.isConfigured()) {
            throw new ClovaSpeechException("CLOVA Speech 설정이 비어 있습니다.", null, null);
        }
        if (callbackSecret == null || callbackSecret.isBlank()) {
            throw new ClovaSpeechException("callback 비밀값이 비어 있습니다.", null, null);
        }
        Path audioPath = Path.of(audioFilePath);
        if (!Files.isRegularFile(audioPath)) {
            throw new ClovaSpeechException("전사할 음성 파일이 없습니다.", null, null);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", new FileSystemResource(audioPath));
        body.add("params", objectMapper.writeValueAsString(requestParams(callbackSecret)));

        try {
            ClovaSpeechJobResponse response = restClient.post()
                    .uri(properties.invokeUrl() + UPLOAD_PATH)
                    .header(API_KEY_HEADER, properties.secretKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ClovaSpeechJobResponse.class);
            return extractToken(response);
        } catch (RestClientResponseException e) {
            throw new ClovaSpeechException("CLOVA Speech 요청이 거부되었습니다.", e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new ClovaSpeechException("CLOVA Speech 요청에 실패했습니다.", null, e);
        }
    }

    private Map<String, Object> requestParams(String callbackSecret) {
        return Map.of(
                "language", LANGUAGE,
                "completion", "async",
                "callback", callbackUrl(callbackSecret),
                "fullText", true,
                // 단어별 시각은 쓰지 않으므로 받지 않아 callback 본문을 줄인다
                "wordAlignment", false,
                "diarization", Map.of(
                        "enable", true,
                        "speakerCountMin", SPEAKER_COUNT,
                        "speakerCountMax", SPEAKER_COUNT
                )
        );
    }

    // 비밀값이 담긴 주소는 로그에 남기지 않는다
    private String callbackUrl(String callbackSecret) {
        String baseUrl = properties.callbackBaseUrl();
        return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + callbackSecret;
    }

    @Override
    public RecognitionResult parseResult(String rawBody) {
        ClovaSpeechResponse response;
        try {
            response = objectMapper.readValue(rawBody, ClovaSpeechResponse.class);
        } catch (RuntimeException e) {
            throw new GeneralException(TranscriptionErrorCode.INVALID_CALLBACK);
        }
        if (response == null || response.result() == null) {
            throw new GeneralException(TranscriptionErrorCode.INVALID_CALLBACK);
        }

        List<RecognizedSegment> segments = response.segments() == null ? List.of() : response.segments().stream()
                .map(segment -> new RecognizedSegment(
                        segment.speaker() == null ? null : segment.speaker().label(),
                        segment.start(),
                        segment.end(),
                        segment.text()))
                .toList();
        return new RecognitionResult(COMPLETED.equals(response.result()), response.token(), segments);
    }

    private String extractToken(ClovaSpeechJobResponse response) {
        if (response == null || response.token() == null || response.token().isBlank()) {
            throw new ClovaSpeechException("CLOVA Speech 응답에 작업 토큰이 없습니다.", null, null);
        }
        return response.token();
    }
}
