package com.example.resay.global.infrastructure.liner;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
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

        LinerApiException lastException = null;
        for (int attempt = 1; attempt <= properties.retry().maxAttempts(); attempt++) {
            try {
                return chatOnce(request);
            } catch (LinerApiException exception) {
                lastException = exception;
                if (!exception.isRetryable() || attempt == properties.retry().maxAttempts()) {
                    throw exception;
                }
                waitBeforeRetry(exception, attempt);
            }
        }
        throw lastException;
    }

    private LinerChatResult chatOnce(LinerChatRequest request) {
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
                    isTimeout(exception) ? "timeout" : "transport_error",
                    true,
                    null,
                    null,
                    exception
            );
        } catch (RestClientException exception) {
            throw new LinerApiException(
                    null,
                    "invalid_response",
                    false,
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
            throw new LinerApiException(null, "invalid_response", false, null, null, null);
        }
    }

    private LinerApiException toApiException(RestClientResponseException exception) {
        LinerErrorResponse errorResponse = parseErrorResponse(exception.getResponseBodyAsString());
        LinerErrorResponse.ErrorDetail error = errorResponse != null ? errorResponse.error() : null;
        HttpHeaders headers = exception.getResponseHeaders();

        return new LinerApiException(
                exception.getStatusCode().value(),
                error != null && error.code() != null ? error.code() : "unknown_error",
                isTransientStatus(exception.getStatusCode().value()),
                parseRetryAfter(headers),
                headers != null ? headers.getFirst(REQUEST_ID_HEADER) : null,
                error != null ? error.message() : null,
                null
        );
    }

    private boolean isTransientStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void waitBeforeRetry(LinerApiException exception, int failedAttempt) {
        Duration configuredDelay = properties.retry().initialDelay().multipliedBy(1L << (failedAttempt - 1));
        Duration requestedDelay = exception.getRetryAfter() != null
                ? exception.getRetryAfter()
                : configuredDelay;
        Duration delay = requestedDelay.compareTo(properties.retry().maxDelay()) > 0
                ? properties.retry().maxDelay()
                : requestedDelay;
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            exception.addSuppressed(interruptedException);
            throw exception;
        }
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
