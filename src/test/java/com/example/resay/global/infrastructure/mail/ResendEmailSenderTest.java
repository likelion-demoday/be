package com.example.resay.global.infrastructure.mail;

import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTest {

    private static final String BASE_URL = "https://api.resend.test";
    private static final String FROM = "RE:SAY <noreply@resay.site>";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private MockRestServiceServer server;

    private ResendEmailSender sender(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        ResendProperties properties = new ResendProperties(apiKey, BASE_URL, FROM, null, null);
        return new ResendEmailSender(properties, builder.build(), OBJECT_MAPPER);
    }

    private static EmailMessage message() {
        return new EmailMessage("user@example.com", "제목", "<p>본문</p>", "본문", "analysis-completed-1", Map.of());
    }

    @Test
    void 키와_보내는_주소를_담아_보내고_발송_ID를_돌려준다() {
        ResendEmailSender sender = sender("re_test_key");
        server.expect(requestTo(BASE_URL + "/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", "analysis-completed-1"))
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("제목"))
                .andExpect(jsonPath("$.html").value("<p>본문</p>"))
                .andExpect(jsonPath("$.text").value("본문"))
                .andExpect(jsonPath("$.headers").doesNotExist())
                .andRespond(withSuccess("{\"id\": \"msg-123\"}", MediaType.APPLICATION_JSON));

        EmailSendResult result = sender.send(message());

        assertThat(result).isEqualTo(EmailSendResult.sent("msg-123"));
        server.verify();
    }

    @Test
    void 추가_헤더가_있으면_같이_보낸다() {
        ResendEmailSender sender = sender("re_test_key");
        server.expect(requestTo(BASE_URL + "/emails"))
                .andExpect(jsonPath("$.headers.List-Unsubscribe").value("<https://resay.site/mypage/notifications>"))
                .andRespond(withSuccess("{\"id\": \"msg-123\"}", MediaType.APPLICATION_JSON));

        sender.send(new EmailMessage("user@example.com", "제목", "<p>본문</p>", "본문", null,
                Map.of("List-Unsubscribe", "<https://resay.site/mypage/notifications>")));

        server.verify();
    }

    @Test
    void 키가_없으면_보내지_않는다() {
        ResendEmailSender sender = sender("");
        server.expect(never(), anything());

        assertThat(sender.send(message())).isEqualTo(EmailSendResult.notConfigured());
        server.verify();
    }

    @Test
    void 거절되면_상태_코드와_오류_종류만_담아_실패한다() {
        ResendEmailSender sender = sender("re_test_key");
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"statusCode": 403, "name": "validation_error",
                                 "message": "You can only send testing emails to your own email address (user@example.com)."}
                                """));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(EmailSendException.class)
                .extracting(exception -> ((EmailSendException) exception).getReason())
                .isEqualTo("HTTP_403 validation_error");
    }

    @Test
    void 서버_오류도_실패로_알린다() {
        ResendEmailSender sender = sender("re_test_key");
        server.expect(requestTo(BASE_URL + "/emails")).andRespond(withServerError());

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(EmailSendException.class)
                .extracting(exception -> ((EmailSendException) exception).getReason())
                .isEqualTo("HTTP_500 unknown");
    }

    @Test
    void 발송_ID가_없는_응답은_실패로_본다() {
        ResendEmailSender sender = sender("re_test_key");
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(EmailSendException.class)
                .extracting(exception -> ((EmailSendException) exception).getReason())
                .isEqualTo("invalid_response");
    }
}
