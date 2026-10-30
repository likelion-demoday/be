package com.example.resay.global.infrastructure.image;

import java.io.IOException;
import java.time.Duration;
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

class OpenAiImageApiClientTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String GENERATIONS_URL = BASE_URL + "/v1/images/generations";

    private MockRestServiceServer openAiServer;
    private OpenAiImageApiClient apiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        openAiServer = MockRestServiceServer.bindTo(builder).build();
        OpenAiImageProperties properties = properties("openai-api-key");
        apiClient = new OpenAiImageApiClient(
                properties,
                builder.build(),
                new ObjectMapper()
        );
    }

    @Test
    void sendsGenerationRequestAndParsesResponse() {
        openAiServer.expect(requestTo(GENERATIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer openai-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "gpt-image-2.5-flare",
                          "prompt": "character prompt",
                          "n": 1,
                          "size": "1024x1024",
                          "quality": "medium",
                          "output_format": "png",
                          "background": "transparent",
                          "moderation": "auto"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "created": 1786521600,
                          "data": [{"b64_json": "aW1hZ2U="}],
                          "output_format": "png",
                          "quality": "medium",
                          "size": "1024x1024"
                        }
                        """, MediaType.APPLICATION_JSON)
                        .header("x-request-id", "openai-request-123"));

        OpenAiImageResult result = apiClient.generate(request());

        assertThat(result.requestId()).isEqualTo("openai-request-123");
        assertThat(result.base64Image()).isEqualTo("aW1hZ2U=");
        assertThat(result.response().outputFormat()).isEqualTo("png");
        openAiServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 429, 500, 502})
    void marksTransientErrorsAsRetryable(int statusCode) {
        openAiServer.expect(requestTo(GENERATIONS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Retry-After", "2")
                        .header("x-request-id", "openai-request-error")
                        .body("""
                                {
                                  "error": {
                                    "message": "try again later",
                                    "type": "server_error",
                                    "code": "provider_error"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> apiClient.generate(request()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(statusCode);
                    assertThat(exception.getErrorCode()).isEqualTo("provider_error");
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(exception.getRequestId()).isEqualTo("openai-request-error");
                    assertThat(exception.getProviderMessage()).isEqualTo("try again later");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 403})
    void doesNotRetryPermanentErrors(int statusCode) {
        openAiServer.expect(requestTo(GENERATIONS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "request rejected",
                                    "type": "invalid_request_error",
                                    "code": "moderation_blocked"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> apiClient.generate(request()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception ->
                        assertThat(exception.isRetryable()).isFalse()
                );
    }

    @Test
    void rejectsEmptySuccessResponse() {
        openAiServer.expect(requestTo(GENERATIONS_URL))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.generate(request()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("invalid_response");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void handlesTransportFailure() {
        openAiServer.expect(requestTo(GENERATIONS_URL))
                .andRespond(withException(new IOException("connection failed")));

        assertThatThrownBy(() -> apiClient.generate(request()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("transport_error");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void rejectsCallWhenApiKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiImageApiClient client = new OpenAiImageApiClient(
                properties(null),
                builder.build(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(OpenAiImageApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("not_configured");
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    private OpenAiImageProperties properties(String apiKey) {
        return new OpenAiImageProperties(
                apiKey,
                BASE_URL,
                "gpt-image-2.5-flare",
                "1024x1024",
                "medium",
                "png",
                "transparent",
                Duration.ofSeconds(3),
                Duration.ofSeconds(180)
        );
    }

    private OpenAiImageRequest request() {
        return new OpenAiImageRequest(
                "gpt-image-2.5-flare",
                "character prompt",
                1,
                "1024x1024",
                "medium",
                "png",
                "transparent",
                "auto"
        );
    }
}
