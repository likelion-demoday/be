package com.example.resay.global.infrastructure.image;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiImageApiClient {

    private static final String GENERATIONS_PATH = "/v1/images/generations";
    private static final String REQUEST_ID_HEADER = "x-request-id";

    private final OpenAiImageProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiImageApiClient(
            OpenAiImageProperties properties,
            @Qualifier("openAiImageRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public OpenAiImageResult generate(OpenAiImageRequest request) {
        if (!properties.isConfigured()) {
            throw exception(null, "not_configured", false, null, null, null, null);
        }

        try {
            ResponseEntity<OpenAiImageResponse> entity = restClient.post()
                    .uri(GENERATIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(OpenAiImageResponse.class);

            OpenAiImageResponse response = entity.getBody();
            validateResponse(response);
            return new OpenAiImageResult(
                    entity.getHeaders().getFirst(REQUEST_ID_HEADER),
                    response
            );
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw exception(null, "transport_error", true, null, null, null, exception);
        } catch (RestClientException exception) {
            throw exception(null, "invalid_response", true, null, null, null, exception);
        }
    }

    private void validateResponse(OpenAiImageResponse response) {
        if (response == null
                || response.data().isEmpty()
                || response.data().get(0) == null
                || response.data().get(0).base64Json() == null
                || response.data().get(0).base64Json().isBlank()) {
            throw exception(null, "invalid_response", true, null, null, null, null);
        }
    }

    private OpenAiImageApiException toApiException(RestClientResponseException exception) {
        OpenAiErrorResponse errorResponse = parseErrorResponse(exception.getResponseBodyAsString());
        OpenAiErrorResponse.ErrorDetail error = errorResponse != null ? errorResponse.error() : null;
        HttpHeaders headers = exception.getResponseHeaders();
        int statusCode = exception.getStatusCode().value();

        return exception(
                statusCode,
                error != null && error.code() != null ? error.code() : "unknown_error",
                isRetryable(statusCode),
                parseRetryAfter(headers),
                headers != null ? headers.getFirst(REQUEST_ID_HEADER) : null,
                error != null ? error.message() : null,
                exception
        );
    }

    private OpenAiErrorResponse parseErrorResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, OpenAiErrorResponse.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408
                || statusCode == 409
                || statusCode == 429
                || statusCode >= 500;
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(retryAfter));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private OpenAiImageApiException exception(
            Integer statusCode,
            String errorCode,
            boolean retryable,
            Duration retryAfter,
            String requestId,
            String providerMessage,
            Throwable cause
    ) {
        return new OpenAiImageApiException(
                statusCode,
                errorCode,
                retryable,
                retryAfter,
                requestId,
                providerMessage,
                cause
        );
    }
}
