package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
// AnalysisModelClient를 실제 LINER 호출로 연결하는 어댑터
@Component
public class LinerAnalysisModelClient implements AnalysisModelClient {

    private final LinerApiClient linerApiClient;
    private final LinerAnalysisRequestFactory requestFactory;
    private final LinerAnalysisResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public LinerAnalysisModelClient(
            LinerApiClient linerApiClient,
            LinerAnalysisRequestFactory requestFactory,
            LinerAnalysisResponseValidator responseValidator,
            ObjectMapper objectMapper
    ) {
        this.linerApiClient = linerApiClient;
        this.requestFactory = requestFactory;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisModelResult analyze(AnalysisSource source) {
        LinerChatResult result = linerApiClient.chat(requestFactory.create(source));
        validateCompletion(result);

        LinerAnalysisResponse response = parseResponse(result.content());
        responseValidator.validate(source, response);

        return new AnalysisModelResult(
                serializeResponse(response),
                result.response().model(),
                requestFactory.promptVersion(),
                requestFactory.schemaVersion()
        );
    }

    private void validateCompletion(LinerChatResult result) {
        String finishReason = result.response().choices().get(0).finishReason();
        if (!"stop".equals(finishReason)) {
            throw invalidResponse("LINER 분석 응답이 정상적으로 완료되지 않았습니다.", null);
        }
        if (!StringUtils.hasText(result.response().model())) {
            throw invalidResponse("LINER 분석 응답에 모델 정보가 없습니다.", null);
        }
    }

    private LinerAnalysisResponse parseResponse(String content) {
        try {
            return objectMapper.readValue(content, LinerAnalysisResponse.class);
        } catch (Exception exception) {
            throw invalidResponse("LINER 분석 응답을 해석할 수 없습니다.", exception);
        }
    }

    private String serializeResponse(LinerAnalysisResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new LinerAnalysisException(
                    "analysis_response_serialization_failed",
                    "LINER 분석 결과를 저장 형식으로 변환할 수 없습니다.",
                    exception
            );
        }
    }

    private LinerAnalysisException invalidResponse(String message, Throwable cause) {
        return new LinerAnalysisException("invalid_analysis_response", message, cause);
    }
}
