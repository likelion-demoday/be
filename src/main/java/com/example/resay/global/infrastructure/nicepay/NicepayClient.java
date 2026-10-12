package com.example.resay.global.infrastructure.nicepay;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 나이스페이 결제 API (Basic 인증).
 *
 * 응답은 두 가지로만 나뉜다.
 *  - 나이스페이가 답을 준 경우: 성공이든 실패든 {@link NicepayTransaction}으로 돌려준다.
 *    실패(없는 거래 404, 인증 오류 401 등)도 본문에 결과 코드가 실려 오므로 예외로 바꾸지 않는다
 *  - 답을 받지 못한 경우(타임아웃 · 연결 끊김 · 5xx · 읽을 수 없는 응답): {@link NicepayUnknownResultException}.
 *    요청이 처리됐는지 알 수 없다
 *
 * 로그에는 결과 코드만 남긴다. 응답 본문에는 카드 · 구매자 정보가 들어 있다.
 */
@Slf4j
@Component
public class NicepayClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 나이스페이의 시각은 "2026-10-08T15:30:12.000+0900"처럼 시간대에 콜론이 없다
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
    );

    private final NicepayProperties properties;
    private final NicepaySignature signature;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NicepayClient(
            NicepayProperties properties,
            NicepaySignature signature,
            @Qualifier("nicepayRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.signature = signature;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 결제창에서 인증을 마친 거래를 승인한다. 이 요청이 성공해야 실제 결제가 일어난다.
     * 답을 받지 못하면 결제됐을 수도 있으므로 {@link #netCancel}로 취소를 요청해야 한다.
     */
    public NicepayTransaction approve(String tid, int amount) {
        return call("승인", HttpMethod.POST, Map.of("amount", amount), "/v1/payments/{tid}", tid);
    }

    /**
     * 망취소: 승인 요청의 답을 받지 못했을 때 그 주문의 결제를 취소한다 (승인 후 1시간 이내).
     * 주문번호로 찾으므로 주문번호 하나에 결제 시도가 하나뿐이어야 한다.
     */
    public NicepayTransaction netCancel(String orderId) {
        return call("망취소", HttpMethod.POST, Map.of("orderId", orderId), "/v1/payments/netcancel");
    }

    /**
     * 결제된 거래를 전액 취소한다.
     *
     * @param cancelOrderId 취소 요청마다 새로 만드는 고유 번호 (원래 주문번호가 아니다)
     */
    public NicepayTransaction cancel(String tid, String cancelOrderId, String reason) {
        return call("취소", HttpMethod.POST, Map.of("reason", reason, "orderId", cancelOrderId),
                "/v1/payments/{tid}/cancel", tid);
    }

    /** 거래의 현재 상태를 조회한다. */
    public NicepayTransaction find(String tid) {
        return call("조회", HttpMethod.GET, null, "/v1/payments/{tid}", tid);
    }

    private NicepayTransaction call(
            String operation, HttpMethod method, Map<String, Object> body, String path, Object... pathVariables
    ) {
        if (!properties.isConfigured()) {
            throw new NicepayUnknownResultException("나이스페이 설정이 없습니다.", null);
        }

        NicepayTransaction transaction;
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(path, pathVariables)
                    .headers(headers -> headers.setBasicAuth(properties.clientKey(), properties.secretKey()))
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            transaction = request.exchange((httpRequest, httpResponse) -> {
                int httpStatus = httpResponse.getStatusCode().value();
                // 서버 오류는 요청이 어디까지 처리됐는지 알 수 없다. 본문에 결과 코드가 있더라도 믿지 않는다
                if (httpResponse.getStatusCode().is5xxServerError()) {
                    throw new NicepayUnknownResultException("나이스페이 서버 오류: status=" + httpStatus, null);
                }
                return toTransaction(httpStatus, parse(httpResponse.getBody().readAllBytes()));
            });
        } catch (NicepayUnknownResultException exception) {
            log.warn("나이스페이 {} 결과를 알 수 없습니다: {}", operation, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            // 연결 실패 · 타임아웃 · 응답이 중간에 끊긴 경우. 그 밖의 예상하지 못한 오류도 "답을 받지 못함"으로 본다
            log.warn("나이스페이 {} 응답을 받지 못했습니다: {}", operation, exception.getClass().getSimpleName());
            throw new NicepayUnknownResultException("나이스페이 응답을 받지 못했습니다.", exception);
        }

        if (!transaction.isSuccess()) {
            log.warn("나이스페이 {} 실패: httpStatus={}, resultCode={}",
                    operation, transaction.httpStatus(), transaction.resultCode());
        }
        return transaction;
    }

    private NicepayPaymentResponse parse(byte[] body) {
        NicepayPaymentResponse response;
        try {
            response = objectMapper.readValue(body, NicepayPaymentResponse.class);
        } catch (RuntimeException exception) {
            throw new NicepayUnknownResultException("나이스페이 응답을 읽을 수 없습니다.", exception);
        }
        if (response == null || response.resultCode() == null || response.resultCode().isBlank()) {
            throw new NicepayUnknownResultException("나이스페이 응답에 결과 코드가 없습니다.", null);
        }
        return response;
    }

    private NicepayTransaction toTransaction(int httpStatus, NicepayPaymentResponse response) {
        int amount = response.amount() == null ? 0 : response.amount();
        return new NicepayTransaction(
                httpStatus,
                response.resultCode(),
                response.resultMsg(),
                response.tid(),
                response.orderId(),
                response.status(),
                amount,
                parseDateTime(response.paidAt()),
                response.receiptUrl(),
                signature.checkTransaction(response.tid(), amount, response.ediDate(), response.signature())
        );
    }

    // 시각을 읽지 못해도 결제 처리는 계속돼야 하므로 예외 대신 null을 돌려준다
    static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return OffsetDateTime.parse(value, format).atZoneSameInstant(SEOUL).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 시도한다
            }
        }
        return null;
    }
}
