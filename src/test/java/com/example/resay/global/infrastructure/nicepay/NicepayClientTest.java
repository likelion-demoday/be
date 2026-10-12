package com.example.resay.global.infrastructure.nicepay;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NicepayClientTest {

    private static final String BASE_URL = "https://sandbox-api.nicepay.co.kr";
    private static final String CLIENT_KEY = "S2_test-client-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String TID = "UT0000113m01012610081530120001";
    private static final String ORDER_ID = "RS2610080123456789abcdef0123456789abcdef";
    // sha256(TID + "3000" + "2026-10-08T15:30:12.000+0900" + SECRET_KEY). 테스트 밖에서 계산한 고정값
    private static final String SIGNATURE = "c81452b64c538ec59e1915c556f288a5c535a4fff90b53b6f4cc7baa6abe151e";

    private MockRestServiceServer nicepayServer;
    private NicepayClient nicepayClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        nicepayServer = MockRestServiceServer.bindTo(builder).build();
        nicepayClient = client(properties(CLIENT_KEY, SECRET_KEY), builder.build());
    }

    @Test
    void approvesWithBasicAuthAndAmount() {
        String credentials = Base64.getEncoder()
                .encodeToString((CLIENT_KEY + ":" + SECRET_KEY).getBytes(StandardCharsets.UTF_8));
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + credentials))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"amount": 3000}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(paidBody(3000), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction = nicepayClient.approve(TID, 3000);

        assertThat(transaction.isSuccess()).isTrue();
        assertThat(transaction.isPaid()).isTrue();
        assertThat(transaction.tid()).isEqualTo(TID);
        assertThat(transaction.orderId()).isEqualTo(ORDER_ID);
        assertThat(transaction.amount()).isEqualTo(3000);
        assertThat(transaction.paidAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 30, 12));
        assertThat(transaction.receiptUrl()).isEqualTo("https://npg.nicepay.co.kr/issue/IssueLoader.do?TID=" + TID);
        assertThat(transaction.signature()).isEqualTo(Signature.VALID);
        assertThat(transaction.httpStatus()).isEqualTo(200);
        assertThat(transaction.isNotPaid()).isFalse();
        nicepayServer.verify();
    }

    // 응답의 금액이 서명한 값과 다르면(중간에 바뀌었으면) 서명이 맞지 않는다
    @Test
    void marksSignatureInvalidWhenResponseWasAltered() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess(paidBody(30000), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction = nicepayClient.approve(TID, 3000);

        assertThat(transaction.isPaid()).isTrue();
        assertThat(transaction.amount()).isEqualTo(30000);
        assertThat(transaction.signature()).isEqualTo(Signature.INVALID);
    }

    // 서명과 ediDate는 응답의 필수 항목이 아니다. 없는 것과 틀린 것을 구분한다
    @Test
    void tellsMissingSignatureFromWrongOne() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("""
                        {"resultCode": "0000", "resultMsg": "정상 처리되었습니다.", "tid": "%s", "orderId": "%s",
                         "status": "paid", "paidAt": "2026-10-08T15:30:12.000+0900", "amount": 3000}
                        """.formatted(TID, ORDER_ID), MediaType.APPLICATION_JSON));
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("""
                        {"resultCode": "0000", "resultMsg": "정상 처리되었습니다.", "tid": "%s", "orderId": "%s",
                         "status": "paid", "paidAt": "2026-10-08T15:30:12.000+0900", "amount": 3000,
                         "ediDate": null, "signature": "%s"}
                        """.formatted(TID, ORDER_ID, SIGNATURE), MediaType.APPLICATION_JSON));

        assertThat(nicepayClient.approve(TID, 3000).signature()).isEqualTo(Signature.MISSING);
        assertThat(nicepayClient.approve(TID, 3000).signature()).isEqualTo(Signature.MISSING);
    }

    // 카드사 거절 등: 나이스페이가 결제되지 않았다고 답했다. 예외가 아니라 실패 결과로 돌려준다
    @Test
    void returnsRejectionWhenApprovalIsDeclined() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("""
                        {"resultCode": "3095", "resultMsg": "카드사 실패 응답", "tid": "%s", "orderId": "%s",
                         "ediDate": null, "signature": null, "status": "failed", "paidAt": "0", "failedAt": "0",
                         "amount": 3000, "receiptUrl": null, "card": null}
                        """.formatted(TID, ORDER_ID), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction = nicepayClient.approve(TID, 3000);

        assertThat(transaction.isSuccess()).isFalse();
        assertThat(transaction.isPaid()).isFalse();
        assertThat(transaction.resultCode()).isEqualTo("3095");
        assertThat(transaction.resultMsg()).isEqualTo("카드사 실패 응답");
        assertThat(transaction.paidAt()).isNull();
        assertThat(transaction.signature()).isEqualTo(Signature.MISSING);
        // 요청이 거절된 응답은 조회 결과로서의 "결제되지 않음"이 아니다 (상태를 조회한 답이 아니다)
        assertThat(transaction.isNotPaid()).isFalse();
    }

    // 샌드박스에서 확인한 형식: 없는 거래는 HTTP 404와 함께 결과 코드가 온다
    @Test
    void returnsRejectionForHttp404WithResultCode() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(emptyBody("U121", "인증 요청내역이 존재하지 않습니다.")));

        NicepayTransaction transaction = nicepayClient.approve(TID, 3000);

        assertThat(transaction.isSuccess()).isFalse();
        assertThat(transaction.resultCode()).isEqualTo("U121");
        assertThat(transaction.httpStatus()).isEqualTo(404);
    }

    // 샌드박스에서 확인한 형식: 키가 틀리면 HTTP 401에 결과 코드와 메시지만 온다
    @Test
    void returnsRejectionForAuthenticationFailure() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"resultCode": "U116", "resultMsg": "사용자 정보가 존재하지 않습니다."}
                                """));

        NicepayTransaction transaction = nicepayClient.approve(TID, 3000);

        assertThat(transaction.isSuccess()).isFalse();
        assertThat(transaction.resultCode()).isEqualTo("U116");
        assertThat(transaction.amount()).isZero();
        // 키 오류로 조회가 거절된 것은 "결제되지 않음"이 아니다
        assertThat(transaction.httpStatus()).isEqualTo(401);
        assertThat(transaction.isNotFound()).isFalse();
        assertThat(transaction.isNotPaid()).isFalse();
    }

    // 응답을 못 받으면 결제됐는지 알 수 없다. 실패 결과가 아니라 "알 수 없음"으로 구분해야 망취소로 이어진다
    @Test
    void reportsUnknownResultWhenResponseTimesOut() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> nicepayClient.approve(TID, 3000))
                .isInstanceOf(NicepayUnknownResultException.class);
    }

    // 예상하지 못한 오류도 "답을 받지 못함"으로 본다 (결제 여부를 모르는 채 실패로 처리되지 않게)
    @Test
    void reportsUnknownResultForUnexpectedError() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(request -> {
                    throw new IllegalStateException("예상하지 못한 오류");
                });

        assertThatThrownBy(() -> nicepayClient.approve(TID, 3000))
                .isInstanceOf(NicepayUnknownResultException.class);
    }

    @Test
    void reportsUnknownResultForServerError() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(emptyBody("9999", "시스템 오류")));

        assertThatThrownBy(() -> nicepayClient.approve(TID, 3000))
                .isInstanceOf(NicepayUnknownResultException.class);
    }

    @Test
    void reportsUnknownResultWhenBodyIsNotReadable() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("<html>Service Temporarily Unavailable</html>", MediaType.TEXT_HTML));
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withSuccess("{\"status\": \"paid\"}", MediaType.APPLICATION_JSON));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> nicepayClient.approve(TID, 3000))
                    .isInstanceOf(NicepayUnknownResultException.class);
        }
    }

    @Test
    void netCancelsByOrderId() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/netcancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderId": "%s"}
                        """.formatted(ORDER_ID), JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"resultCode": "0000", "resultMsg": "정상 처리되었습니다.", "tid": "%s", "orderId": "%s",
                         "status": "cancelled", "paidAt": "2026-10-08T15:30:12.000+0900",
                         "cancelledAt": "2026-10-08T15:31:00.000+0900", "amount": 3000}
                        """.formatted(TID, ORDER_ID), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction = nicepayClient.netCancel(ORDER_ID);

        assertThat(transaction.isSuccess()).isTrue();
        assertThat(transaction.isPaid()).isFalse();
        assertThat(transaction.isCancelled()).isTrue();
        assertThat(transaction.isNotPaid()).isTrue();
        nicepayServer.verify();
    }

    @Test
    void cancelsByTidWithReasonAndNewCancelOrderId() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"reason": "주문 정보 불일치", "orderId": "RC0123456789abcdef0123456789abcdef"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"resultCode": "0000", "resultMsg": "정상 처리되었습니다.", "tid": "%s", "orderId": "%s",
                         "status": "cancelled", "paidAt": "2026-10-08T15:30:12.000+0900",
                         "cancelledAt": "2026-10-08T15:31:00.000+0900", "amount": 3000, "balanceAmt": 0}
                        """.formatted(TID, ORDER_ID), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction =
                nicepayClient.cancel(TID, "RC0123456789abcdef0123456789abcdef", "주문 정보 불일치");

        assertThat(transaction.isCancelled()).isTrue();
        nicepayServer.verify();
    }

    // 샌드박스에서 확인한 형식: 취소 요청이 받아들여지지 않으면(필수값 누락 등) HTTP 200에 결과 코드만 다르다
    @Test
    void doesNotTreatRejectedCancelAsCancelled() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID + "/cancel"))
                .andRespond(withSuccess(emptyBody("2013", "기취소 거래"), MediaType.APPLICATION_JSON));

        assertThat(nicepayClient.cancel(TID, "RC0123456789abcdef0123456789abcdef", "주문 정보 불일치").isCancelled())
                .isFalse();
    }

    @Test
    void findsTransactionByTid() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paidBody(3000), MediaType.APPLICATION_JSON));

        NicepayTransaction transaction = nicepayClient.find(TID);

        assertThat(transaction.isPaid()).isTrue();
        assertThat(transaction.isCancelled()).isFalse();
        assertThat(transaction.signature()).isEqualTo(Signature.VALID);
        nicepayServer.verify();
    }

    @Test
    void tellsWhenTransactionDoesNotExist() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(emptyBody("U107", "거래내역이 존재 하지 않습니다.")));

        NicepayTransaction transaction = nicepayClient.find(TID);

        assertThat(transaction.isSuccess()).isFalse();
        assertThat(transaction.isNotFound()).isTrue();
        assertThat(transaction.isNotPaid()).isTrue();
    }

    // 주소 설정이 틀려서 나는 404(그런 페이지 없음)는 "거래 없음"이 아니다. 결제된 주문을 실패로 끝내면 안 된다
    @Test
    void doesNotTreatOther404AsMissingTransaction() {
        nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(emptyBody("U320", "페이지를 찾을 수 없습니다.")));

        NicepayTransaction transaction = nicepayClient.find(TID);

        assertThat(transaction.isNotFound()).isFalse();
        assertThat(transaction.isNotPaid()).isFalse();
    }

    // 인증만 하고 승인되지 않은 거래 등 결제되지 않았음이 분명한 상태와, 처음 보는 상태를 구분한다
    @Test
    void classifiesLookupStatus() {
        for (String status : new String[]{"ready", "failed", "cancelled", "expired", "partialCancelled", ""}) {
            nicepayServer.expect(requestTo(BASE_URL + "/v1/payments/" + TID))
                    .andRespond(withSuccess("""
                            {"resultCode": "0000", "resultMsg": "정상 처리되었습니다.", "tid": "%s", "orderId": "%s",
                             "status": "%s", "paidAt": "0", "amount": 3000}
                            """.formatted(TID, ORDER_ID, status), MediaType.APPLICATION_JSON));
        }

        assertThat(nicepayClient.find(TID).isNotPaid()).isTrue();
        assertThat(nicepayClient.find(TID).isNotPaid()).isTrue();
        assertThat(nicepayClient.find(TID).isNotPaid()).isTrue();
        assertThat(nicepayClient.find(TID).isNotPaid()).isTrue();
        // 부분 취소(우리가 쓰지 않는 상태)나 빈 상태는 단정하지 않는다
        assertThat(nicepayClient.find(TID).isNotPaid()).isFalse();
        assertThat(nicepayClient.find(TID).isNotPaid()).isFalse();
    }

    @Test
    void doesNotCallNicepayWhenKeysAreMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NicepayClient unconfigured = client(properties("", ""), builder.build());

        assertThatThrownBy(() -> unconfigured.approve(TID, 3000))
                .isInstanceOf(NicepayUnknownResultException.class);
        server.verify();
    }

    @Test
    void parsesNicepayDateTimeAsKoreanTime() {
        assertThat(NicepayClient.parseDateTime("2026-10-08T15:30:12.000+0900"))
                .isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 30, 12));
        assertThat(NicepayClient.parseDateTime("2026-10-08T06:30:12Z"))
                .isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 30, 12));
        // 결제 완료가 아니면 "0"이 온다
        assertThat(NicepayClient.parseDateTime("0")).isNull();
        assertThat(NicepayClient.parseDateTime(null)).isNull();
        assertThat(NicepayClient.parseDateTime("")).isNull();
    }

    private static NicepayClient client(NicepayProperties properties, RestClient restClient) {
        return new NicepayClient(properties, new NicepaySignature(properties), restClient, new ObjectMapper());
    }

    private static NicepayProperties properties(String clientKey, String secretKey) {
        return new NicepayProperties(
                clientKey, secretKey, BASE_URL, "http://localhost:8080/api/v1/payments/nicepay/return",
                Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    // 실제 응답처럼 우리가 읽지 않는 항목(카드 정보 등)도 함께 넣는다
    private static String paidBody(int amount) {
        return """
                {
                  "resultCode": "0000",
                  "resultMsg": "정상 처리되었습니다.",
                  "tid": "%s",
                  "cancelledTid": null,
                  "orderId": "%s",
                  "ediDate": "2026-10-08T15:30:12.000+0900",
                  "signature": "%s",
                  "status": "paid",
                  "paidAt": "2026-10-08T15:30:12.000+0900",
                  "failedAt": "0",
                  "cancelledAt": "0",
                  "payMethod": "card",
                  "amount": %d,
                  "balanceAmt": %d,
                  "goodsName": "Resay 크레딧 3,000",
                  "mallReserved": null,
                  "useEscrow": false,
                  "currency": "KRW",
                  "channel": "pc",
                  "approveNo": "000000",
                  "buyerName": null,
                  "receiptUrl": "https://npg.nicepay.co.kr/issue/IssueLoader.do?TID=%s",
                  "issuedCashReceipt": false,
                  "coupon": null,
                  "card": {
                    "cardCode": "04",
                    "cardName": "삼성",
                    "cardNum": "123412******1234",
                    "cardQuota": 0,
                    "isInterestFree": false,
                    "cardType": "credit",
                    "canPartCancel": true,
                    "acquCardCode": "04",
                    "acquCardName": "삼성"
                  },
                  "vbank": null,
                  "cancels": null,
                  "cashReceipts": null,
                  "messageSource": "nicepay"
                }
                """.formatted(TID, ORDER_ID, SIGNATURE, amount, amount, TID);
    }

    private static String emptyBody(String resultCode, String resultMsg) {
        return """
                {"resultCode": "%s", "resultMsg": "%s", "tid": "", "cancelledTid": null, "orderId": "",
                 "ediDate": null, "signature": null, "status": "", "paidAt": "0", "failedAt": "0",
                 "cancelledAt": "0", "payMethod": "", "amount": 0, "balanceAmt": 0, "card": null}
                """.formatted(resultCode, resultMsg);
    }
}
