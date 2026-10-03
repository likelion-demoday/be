package com.example.resay.global.infrastructure.liner;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LinerApiClientTest {

    private static final String BASE_URL = "https://platform.liner.com";
    private static final String CHAT_COMPLETIONS_URL = BASE_URL + "/api/v1/chat/completions";

    private MockRestServiceServer linerServer;
    private LinerApiClient linerApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        linerServer = MockRestServiceServer.bindTo(builder).build();
        LinerProperties properties = new LinerProperties(
                "liner-api-key",
                BASE_URL,
                "liner-mark-1.1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(120)
        );
        linerApiClient = new LinerApiClient(properties, builder.build(), new ObjectMapper());
    }

    @Test
    void sendsChatCompletionRequestAndParsesResponse() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer liner-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "liner-mark-1.1",
                          "messages": [
                            {"role": "system", "content": "대화를 분석하세요."},
                            {"role": "user", "content": "분석 대상 대화"}
                          ],
                          "stream": false,
                          "max_completion_tokens": 4096,
                          "reasoning_effort": "medium",
                          "response_format": {"type": "json_object"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-123",
                          "object": "chat.completion",
                          "created": 1786521600,
                          "model": "liner-mark-1.1",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "{\\\"summary\\\":\\\"대화 요약\\\"}"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 100,
                            "completion_tokens": 20,
                            "total_tokens": 120,
                            "prompt_tokens_details": {"cached_tokens": 0}
                          }
                        }
                        """, MediaType.APPLICATION_JSON)
                        .header("x-request-id", "liner-request-123"));

        LinerChatResult result = linerApiClient.chat(request());

        assertThat(result.requestId()).isEqualTo("liner-request-123");
        assertThat(result.response().id()).isEqualTo("chatcmpl-123");
        assertThat(result.response().model()).isEqualTo("liner-mark-1.1");
        assertThat(result.content()).isEqualTo("{\"summary\":\"대화 요약\"}");
        assertThat(result.response().usage().totalTokens()).isEqualTo(120);
        linerServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 413, 429, 500, 502})
    void preservesSupportedErrorStatus(int statusCode) {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "code": "provider_error",
                                    "message": "요청 처리 실패",
                                    "retryable": false,
                                    "type": "invalid_request_error",
                                    "param": "model"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(statusCode);
                    assertThat(exception.getErrorCode()).isEqualTo("provider_error");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void parsesRetryInformationAndRequestId() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(429))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Retry-After", "1")
                        .header("x-request-id", "liner-request-429")
                        .body("""
                                {
                                  "error": {
                                    "code": "RATE_LIMITED",
                                    "message": "요청 한도 초과",
                                    "retryable": true
                                  }
                                }
                                """));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(429);
                    assertThat(exception.getErrorCode()).isEqualTo("RATE_LIMITED");
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(exception.getRequestId()).isEqualTo("liner-request-429");
                    assertThat(exception.getProviderMessage()).isEqualTo("요청 한도 초과");
                    assertThat(exception.getMessage())
                            .contains("statusCode=429")
                            .contains("errorCode=RATE_LIMITED")
                            .contains("requestId=liner-request-429")
                            .contains("providerMessage=요청 한도 초과");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void usesUnknownErrorCodeForMalformedErrorResponse() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(500))
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("unexpected error"));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("unknown_error")
                );
    }

    @Test
    void rejectsInvalidSuccessResponse() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-123",
                          "model": "liner-mark-1.1",
                          "choices": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("invalid_response");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void rejectsMalformedSuccessResponse() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("invalid_response");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void handlesTransportFailure() {
        linerServer.expect(requestTo(CHAT_COMPLETIONS_URL))
                .andRespond(withException(new IOException("connection failed")));

        assertThatThrownBy(() -> linerApiClient.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("transport_error");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void rejectsCallWhenApiKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LinerProperties properties = new LinerProperties(null, BASE_URL, null, null, null);
        LinerApiClient client = new LinerApiClient(properties, builder.build(), new ObjectMapper());

        assertThatThrownBy(() -> client.chat(request()))
                .isInstanceOfSatisfying(LinerApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("not_configured");
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    private LinerChatRequest request() {
        return new LinerChatRequest(
                "liner-mark-1.1",
                List.of(
                        new LinerChatMessage("system", "대화를 분석하세요."),
                        new LinerChatMessage("user", "분석 대상 대화")
                ),
                false,
                4096,
                "medium",
                Map.of("type", "json_object")
        );
    }
}
