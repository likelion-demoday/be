package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final LinerAnalysisResponseGrounder responseGrounder;
    private final LinerAnalysisResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public LinerAnalysisModelClient(
            LinerApiClient linerApiClient,
            LinerAnalysisRequestFactory requestFactory,
            LinerAnalysisResponseGrounder responseGrounder,
            LinerAnalysisResponseValidator responseValidator,
            ObjectMapper objectMapper
    ) {
        this.linerApiClient = linerApiClient;
        this.requestFactory = requestFactory;
        this.responseGrounder = responseGrounder;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisModelResult analyze(AnalysisSource source) {
        LinerChatResult result;
        try {
            result = linerApiClient.chat(requestFactory.create(source));
        } catch (LinerApiException exception) {
            throw apiFailure(exception);
        }
        validateCompletion(result);

        QualitativeAnalysis response = parseResponse(result.content());
        QualitativeAnalysis groundedResponse = responseGrounder.ground(source, response);
        QualitativeAnalysis validatedResponse = validateWithBestEffortFallback(
                source,
                groundedResponse
        );

        return new AnalysisModelResult(
                serializeResponse(validatedResponse),
                result.response().model(),
                requestFactory.promptVersion(),
                requestFactory.schemaVersion()
        );
    }

    private QualitativeAnalysis validateWithBestEffortFallback(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        try {
            responseValidator.validate(source, response);
            return response;
        } catch (LinerAnalysisException validationException) {
            if (validationException.getCategory()
                    != AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR) {
                throw validationException;
            }

            QualitativeAnalysis fallbackResponse = response;
            if (response.representativeEvidences() != null
                    && !response.representativeEvidences().isEmpty()) {
                fallbackResponse = withoutRepresentativeEvidences(response);
                try {
                    responseValidator.validate(source, fallbackResponse);
                    log.warn(
                            "대표 근거 검증 실패로 부가 항목 제외: "
                                    + "recordingId={}, scenario={}, 제외 {}건",
                            source.recordingId(),
                            source.scenario(),
                            response.representativeEvidences().size()
                    );
                    return fallbackResponse;
                } catch (LinerAnalysisException representativeFallbackException) {
                    if (representativeFallbackException.getCategory()
                            != AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR) {
                        throw representativeFallbackException;
                    }
                    if (validationException != representativeFallbackException) {
                        validationException.addSuppressed(representativeFallbackException);
                    }
                }
            }

            QualitativeAnalysis coreResponse = withoutOptionalReportFields(fallbackResponse);
            if (!coreResponse.equals(fallbackResponse)) {
                try {
                    responseValidator.validate(source, coreResponse);
                    logBestEffortFallback(
                            source,
                            response,
                            coreResponse,
                            validationException,
                            false
                    );
                    return coreResponse;
                } catch (LinerAnalysisException coreValidationException) {
                    if (coreValidationException.getCategory()
                            != AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR) {
                        throw coreValidationException;
                    }
                    if (validationException != coreValidationException) {
                        validationException.addSuppressed(coreValidationException);
                    }
                }
            }

            // JSON Schema 파싱과 Grounder 보정까지 끝난 응답은 가능한 범위에서 저장한다.
            // 화면 조립에 꼭 필요한 타임라인·주제 근거가 깨졌다면 기존 검증 실패를 유지한다.
            if (!isRenderableCore(source, fallbackResponse)) {
                throw validationException;
            }
            logBestEffortFallback(
                    source,
                    response,
                    fallbackResponse,
                    validationException,
                    true
            );
            return fallbackResponse;
        }
    }

    private boolean isRenderableCore(AnalysisSource source, QualitativeAnalysis response) {
        if (response.overview() == null
                || response.timeline() == null || response.timeline().isEmpty()
                || response.topics() == null || response.topics().isEmpty()
                || response.characterInsights() == null
                || response.speakerInsights() == null
                || response.interestInsights() == null
                || response.spicinessInsights() == null
                || response.reactionStyleInsights() == null
                || response.scenarioInsights() == null) {
            return false;
        }

        Set<Long> segmentIds = source.segments().stream()
                .map(AnalysisSegment::segmentId)
                .collect(Collectors.toSet());
        boolean timelineRenderable = response.timeline().stream().allMatch(item ->
                item != null
                        && item.evidenceSegmentIds() != null
                        && !item.evidenceSegmentIds().isEmpty()
                        && segmentIds.containsAll(item.evidenceSegmentIds()));
        boolean topicsRenderable = response.topics().stream().allMatch(topic ->
                topic != null
                        && topic.segmentIds() != null
                        && !topic.segmentIds().isEmpty()
                        && segmentIds.containsAll(topic.segmentIds()));
        return timelineRenderable && topicsRenderable;
    }

    private void logBestEffortFallback(
            AnalysisSource source,
            QualitativeAnalysis originalResponse,
            QualitativeAnalysis fallbackResponse,
            LinerAnalysisException validationException,
            boolean coreValidationFailed
    ) {
        log.warn(
                "분석 응답 의미 검증 실패, best-effort 보고서 저장: "
                        + "recordingId={}, scenario={}, promptVersion={}, schemaVersion={}, "
                        + "제외 대표근거={}건, 관계요약={}, 지표={}건, 화자경향={}건, 코칭={}건, "
                        + "coreValidationFailed={}, reason={}",
                source.recordingId(),
                source.scenario(),
                requestFactory.promptVersion(),
                requestFactory.schemaVersion(),
                excludedCount(
                        originalResponse.representativeEvidences(),
                        fallbackResponse.representativeEvidences()
                ),
                originalResponse.relationshipSummary() != null
                        && fallbackResponse.relationshipSummary() == null,
                excludedCount(originalResponse.reportMetrics(), fallbackResponse.reportMetrics()),
                excludedCount(
                        originalResponse.speakerTendencies(),
                        fallbackResponse.speakerTendencies()
                ),
                excludedCount(
                        originalResponse.coachingSuggestions(),
                        fallbackResponse.coachingSuggestions()
                ),
                coreValidationFailed,
                validationException.getMessage()
        );
    }

    private int excludedCount(List<?> original, List<?> fallback) {
        int originalSize = original == null ? 0 : original.size();
        int fallbackSize = fallback == null ? 0 : fallback.size();
        return Math.max(0, originalSize - fallbackSize);
    }

    private QualitativeAnalysis withoutOptionalReportFields(QualitativeAnalysis source) {
        return new QualitativeAnalysis(
                source.overview(),
                source.timeline(),
                source.topics(),
                source.characterInsights(),
                source.speakerInsights(),
                source.interestInsights(),
                source.spicinessInsights(),
                source.reactionStyleInsights(),
                source.scenarioInsights(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private QualitativeAnalysis withoutRepresentativeEvidences(QualitativeAnalysis source) {
        return new QualitativeAnalysis(
                source.overview(),
                source.timeline(),
                source.topics(),
                source.characterInsights(),
                source.speakerInsights(),
                source.interestInsights(),
                source.spicinessInsights(),
                source.reactionStyleInsights(),
                source.scenarioInsights(),
                List.of(),
                source.relationshipSummary(),
                source.reportMetrics(),
                source.speakerTendencies(),
                source.coachingSuggestions()
        );
    }

    private void validateCompletion(LinerChatResult result) {
        String finishReason = result.response().choices().get(0).finishReason();
        if (!"stop".equals(finishReason)) {
            LinerChatResponse.Usage usage = result.response().usage();
            long completionTokens = usage != null ? usage.completionTokens() : 0;
            AnalysisFailureCategory category = "length".equals(finishReason)
                    ? AnalysisFailureCategory.RESPONSE_LENGTH_EXCEEDED
                    : AnalysisFailureCategory.RESPONSE_INCOMPLETE;
            throw new LinerAnalysisException(
                    category,
                    "incomplete_analysis_response",
                    "LINER 분석 응답이 정상적으로 완료되지 않았습니다. "
                            + "finishReason=" + finishReason
                            + ", completionTokens=" + completionTokens,
                    null
            );
        }
        if (!StringUtils.hasText(result.response().model())) {
            throw responseFormatFailure("LINER 분석 응답에 모델 정보가 없습니다.", null);
        }
    }

    private QualitativeAnalysis parseResponse(String content) {
        try {
            return objectMapper.readValue(content, QualitativeAnalysis.class);
        } catch (Exception exception) {
            throw responseFormatFailure("LINER 분석 응답을 해석할 수 없습니다.", exception);
        }
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

    private LinerAnalysisException responseFormatFailure(String message, Throwable cause) {
        return new LinerAnalysisException(
                AnalysisFailureCategory.RESPONSE_FORMAT_ERROR,
                "invalid_analysis_response",
                message,
                cause
        );
    }

    private LinerAnalysisException apiFailure(LinerApiException exception) {
        AnalysisFailureCategory category;
        Integer statusCode = exception.getStatusCode();
        if ("not_configured".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.PROVIDER_CONFIGURATION;
        } else if (statusCode != null && (statusCode == 401 || statusCode == 403)) {
            category = AnalysisFailureCategory.PROVIDER_AUTHENTICATION;
        } else if (statusCode != null && statusCode == 429) {
            category = AnalysisFailureCategory.PROVIDER_RATE_LIMIT;
        } else if ("timeout".equals(exception.getErrorCode()) || (statusCode != null && statusCode == 408)) {
            category = AnalysisFailureCategory.PROVIDER_TIMEOUT;
        } else if ("transport_error".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.PROVIDER_NETWORK;
        } else if (statusCode != null && statusCode >= 500) {
            category = AnalysisFailureCategory.PROVIDER_SERVER_ERROR;
        } else if ("invalid_response".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.RESPONSE_FORMAT_ERROR;
        } else {
            category = AnalysisFailureCategory.PROVIDER_CONFIGURATION;
        }
        return new LinerAnalysisException(
                category,
                exception.getErrorCode(),
                "외부 분석 서비스 처리에 실패했습니다.",
                exception
        );
    }
}
