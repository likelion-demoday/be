package com.example.resay.global.infrastructure.liner;

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
public class LinerApiClient {

    private static final String CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";
    private static final String REQUEST_ID_HEADER = "x-request-id";

    private final LinerProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LinerApiClient(
            LinerProperties properties,
            @Qualifier("linerRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public LinerChatResult chat(LinerChatRequest request) {
        if (!properties.isConfigured()) {
            throw new LinerApiException(null, "not_configured", false, null, null, null);
        }

        try {
            ResponseEntity<LinerChatResponse> entity = restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(LinerChatResponse.class);

            LinerChatResponse response = entity.getBody();
            validateResponse(response);
            return new LinerChatResult(
                    entity.getHeaders().getFirst(REQUEST_ID_HEADER),
                    response
            );
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw new LinerApiException(
                    null,
                    "transport_error",
                    true,
                    null,
                    null,
                    exception
            );
        } catch (RestClientException exception) {
            throw new LinerApiException(
                    null,
                    "invalid_response",
                    true,
                    null,
                    null,
                    null
            );
        }
    }

    private void validateResponse(LinerChatResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new LinerApiException(null, "invalid_response", true, null, null, null);
        }
    }

    private LinerApiException toApiException(RestClientResponseException exception) {
        LinerErrorResponse errorResponse = parseErrorResponse(exception.getResponseBodyAsString());
        LinerErrorResponse.ErrorDetail error = errorResponse != null ? errorResponse.error() : null;
        HttpHeaders headers = exception.getResponseHeaders();

        return new LinerApiException(
                exception.getStatusCode().value(),
                error != null && error.code() != null ? error.code() : "unknown_error",
                error != null && Boolean.TRUE.equals(error.retryable()),
                parseRetryAfter(headers),
                headers != null ? headers.getFirst(REQUEST_ID_HEADER) : null,
                null
        );
    }

    private LinerErrorResponse parseErrorResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, LinerErrorResponse.class);
        } catch (Exception exception) {
            return null;
        }
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
}
