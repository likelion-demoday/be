package com.example.resay.global.infrastructure.clova;

import com.example.resay.domain.transcription.code.TranscriptionErrorCode;
import com.example.resay.domain.transcription.model.RecognitionResult;
import com.example.resay.domain.transcription.model.RecognitionResult.RecognizedSegment;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClovaSpeechClientTest {

    private static final String INVOKE_URL = "https://clovaspeech-gw.ncloud.com/external/v1/1/invoke-key";
    private static final String UPLOAD_URL = INVOKE_URL + "/recognizer/upload";
    private static final String CALLBACK_BASE_URL = "https://api.resay.site/api/v1/transcriptions/callback";
    private static final String CALLBACK_SECRET = "3f9a1c2e-7b4d-4e8a-9c1f-2d6b8e0a5f73";

    @TempDir
    Path tempDir;

    private Path audioFile;
    private MockRestServiceServer clovaServer;
    private ClovaSpeechClient clovaSpeechClient;

    @BeforeEach
    void setUp() throws IOException {
        audioFile = Files.writeString(tempDir.resolve("test.m4a"), "audio");
        RestClient.Builder builder = RestClient.builder();
        clovaServer = MockRestServiceServer.bindTo(builder).build();
        clovaSpeechClient = new ClovaSpeechClient(
                properties(CALLBACK_BASE_URL), builder.build(), new ObjectMapper());
    }

    @Test
    void requestRecognition_비동기_요청을_보내고_작업_토큰을_반환() {
        clovaServer.expect(requestTo(UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-CLOVASPEECH-API-KEY", "secret-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("\"completion\":\"async\"")))
                .andExpect(content().string(containsString("\"callback\":\"" + CALLBACK_BASE_URL + "/" + CALLBACK_SECRET + "\"")))
                .andExpect(content().string(containsString("\"speakerCountMin\":2")))
                .andExpect(content().string(containsString("\"speakerCountMax\":2")))
                .andExpect(content().string(containsString("\"wordAlignment\":false")))
                .andRespond(withSuccess("""
                        {"token":"job-token","result":"SUCCEEDED","message":"Succeeded"}
                        """, MediaType.APPLICATION_JSON));

        String token = clovaSpeechClient.requestRecognition(audioFile.toString(), CALLBACK_SECRET);

        assertThat(token).isEqualTo("job-token");
        clovaServer.verify();
    }

    @Test
    void parseResult_callback_본문을_화자별_구간으로_바꾼다() {
        RecognitionResult result = clovaSpeechClient.parseResult("""
                {"result":"COMPLETED","message":"Succeeded","token":"job-token",
                 "segments":[{"start":350,"end":2420,"text":"안녕","confidence":0.99,"speaker":{"label":"1","name":"A"}}],
                 "speakers":[{"label":"1","name":"A"}]}
                """);

        assertThat(result.completed()).isTrue();
        assertThat(result.jobToken()).isEqualTo("job-token");
        assertThat(result.segments()).containsExactly(new RecognizedSegment("1", 350, 2420, "안녕"));
    }

    @Test
    void parseResult_COMPLETED가_아니면_실패_결과() {
        RecognitionResult result = clovaSpeechClient.parseResult("""
                {"result":"FAILED","message":"Failed","token":"job-token","segments":[]}
                """);

        assertThat(result.completed()).isFalse();
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void parseResult_형식이_잘못되면_INVALID_CALLBACK() {
        assertThatThrownBy(() -> clovaSpeechClient.parseResult("not json"))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(TranscriptionErrorCode.INVALID_CALLBACK);
        assertThatThrownBy(() -> clovaSpeechClient.parseResult("{}"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void requestRecognition_CLOVA가_거부하면_상태코드를_담아_예외() {
        clovaServer.expect(requestTo(UPLOAD_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> clovaSpeechClient.requestRecognition(audioFile.toString(), CALLBACK_SECRET))
                .isInstanceOf(ClovaSpeechException.class)
                .extracting("statusCode")
                .isEqualTo(401);
    }

    @Test
    void requestRecognition_응답에_토큰이_없으면_예외() {
        clovaServer.expect(requestTo(UPLOAD_URL))
                .andRespond(withSuccess("""
                        {"result":"FAILED","message":"Failed"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clovaSpeechClient.requestRecognition(audioFile.toString(), CALLBACK_SECRET))
                .isInstanceOf(ClovaSpeechException.class);
    }

    @Test
    void requestRecognition_callback_주소가_없으면_요청하지_않고_예외() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClovaSpeechClient client = new ClovaSpeechClient(properties(""), builder.build(), new ObjectMapper());

        assertThatThrownBy(() -> client.requestRecognition(audioFile.toString(), CALLBACK_SECRET))
                .isInstanceOf(ClovaSpeechException.class);
        server.verify(); // 요청이 한 번도 나가지 않았는지 확인
    }

    @Test
    void requestRecognition_음성_파일이_없으면_요청하지_않고_예외() {
        assertThatThrownBy(() -> clovaSpeechClient.requestRecognition(tempDir.resolve("none.m4a").toString(), CALLBACK_SECRET))
                .isInstanceOf(ClovaSpeechException.class);
        clovaServer.verify();
    }

    @Test
    void requestRecognition_callback_비밀값이_없으면_요청하지_않고_예외() {
        assertThatThrownBy(() -> clovaSpeechClient.requestRecognition(audioFile.toString(), " "))
                .isInstanceOf(ClovaSpeechException.class);
        clovaServer.verify();
    }

    private ClovaSpeechProperties properties(String callbackBaseUrl) {
        return new ClovaSpeechProperties(
                INVOKE_URL, "secret-key", callbackBaseUrl, Duration.ofSeconds(3), Duration.ofSeconds(60));
    }
}
