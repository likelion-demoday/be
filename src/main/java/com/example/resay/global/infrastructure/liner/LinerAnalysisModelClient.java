package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
// AnalysisModelClient를 실제 LINER 호출로 연결하는 어댑터
@Slf4j
@Component
public class LinerAnalysisModelClient implements AnalysisModelClient {

    private final LinerApiClient linerApiClient;
    private final LinerAnalysisRequestFactory requestFactory;
    private final LinerAnalysisReviewRequestFactory reviewRequestFactory;
    private final LinerApiRetryExecutor retryExecutor;
    private final LinerAnalysisResponseGrounder responseGrounder;
    private final LinerAnalysisResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public LinerAnalysisModelClient(
            LinerApiClient linerApiClient,
            LinerAnalysisRequestFactory requestFactory,
            LinerAnalysisReviewRequestFactory reviewRequestFactory,
            LinerApiRetryExecutor retryExecutor,
            LinerAnalysisResponseGrounder responseGrounder,
            LinerAnalysisResponseValidator responseValidator,
            ObjectMapper objectMapper
    ) {
        this.linerApiClient = linerApiClient;
        this.requestFactory = requestFactory;
        this.reviewRequestFactory = reviewRequestFactory;
        this.retryExecutor = retryExecutor;
        this.responseGrounder = responseGrounder;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisModelResult analyze(AnalysisSource source) {
        LinerChatResult initialResult = call(
                source.recordingId(),
                LinerAnalysisStage.INITIAL,
                requestFactory.create(source)
        );
        QualitativeAnalysis draftResponse = parseResponse(
                initialResult.content(),
                LinerAnalysisStage.INITIAL
        );

        LinerChatResult finalResult = call(
                source.recordingId(),
                LinerAnalysisStage.REVIEW,
                reviewRequestFactory.create(source, draftResponse)
        );
        QualitativeAnalysis finalResponse = parseResponse(
                finalResult.content(),
                LinerAnalysisStage.REVIEW
        );

        QualitativeAnalysis groundedResponse = responseGrounder.ground(source, finalResponse);
        responseValidator.validate(source, groundedResponse);

        return new AnalysisModelResult(
                serializeResponse(groundedResponse),
                finalResult.response().model(),
                reviewRequestFactory.promptVersion(),
                requestFactory.schemaVersion()
        );
    }

    private LinerChatResult call(
            Long recordingId,
            LinerAnalysisStage stage,
            LinerChatRequest request
    ) {
        long startedAt = System.nanoTime();
        LinerChatResult result = retryExecutor.execute(
                stage,
                () -> linerApiClient.chat(request)
        );
        validateCompletion(result, stage);
        logCompletion(recordingId, stage, result, startedAt);
        return result;
    }

    private void validateCompletion(LinerChatResult result, LinerAnalysisStage stage) {
        String finishReason = result.response().choices().get(0).finishReason();
        if (!"stop".equals(finishReason)) {
            LinerChatResponse.Usage usage = result.response().usage();
            long completionTokens = usage != null ? usage.completionTokens() : 0;
            throw invalidResponse(
                    "LINER 분석 응답이 정상적으로 완료되지 않았습니다. "
                            + "stage=" + stage
                            + ", "
                            + "finishReason=" + finishReason
                            + ", completionTokens=" + completionTokens,
                    null
            );
        }
        if (!StringUtils.hasText(result.response().model())) {
            throw invalidResponse("LINER 분석 응답에 모델 정보가 없습니다. stage=" + stage, null);
        }
    }

    private QualitativeAnalysis parseResponse(String content, LinerAnalysisStage stage) {
        try {
            return objectMapper.readValue(content, QualitativeAnalysis.class);
        } catch (Exception exception) {
            throw invalidResponse(
                    "LINER 분석 응답을 해석할 수 없습니다. stage=" + stage,
                    exception
            );
        }
    }

    private void logCompletion(
            Long recordingId,
            LinerAnalysisStage stage,
            LinerChatResult result,
            long startedAt
    ) {
        LinerChatResponse.Usage usage = result.response().usage();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "LINER 분석 단계 완료: recordingId={}, stage={}, model={}, requestId={}, "
                        + "promptTokens={}, completionTokens={}, totalTokens={}, elapsedMs={}, finishReason={}",
                recordingId,
                stage,
                result.response().model(),
                result.requestId(),
                usage != null ? usage.promptTokens() : null,
                usage != null ? usage.completionTokens() : null,
                usage != null ? usage.totalTokens() : null,
                elapsedMs,
                result.response().choices().get(0).finishReason()
        );
    }

    private String serializeResponse(QualitativeAnalysis response) {
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
