package com.example.resay.global.infrastructure.audio;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioDurationReaderTest {

    // 크롬(MediaRecorder)으로 16초 음성을 녹음한 파일. 헤더의 재생시간이 0인 조각(fragmented) MP4다
    private static final String CHROME_AAC = "audio/chrome-aac.m4a";
    private static final String CHROME_OPUS = "audio/chrome-opus.m4a";
    // 크롬 기본 녹음 형식. 전사 서비스(CLOVA)도 받지 않는다
    private static final String CHROME_WEBM = "audio/chrome-opus.webm";

    @TempDir
    Path tempDir;

    private final AudioDurationReader audioDurationReader = new AudioDurationReader();

    @Test
    void readSeconds_확장자만_바꾼_파일이면_지원하지_않는_포맷() throws Exception {
        Path fakeAudio = tempDir.resolve("fake.mp3");
        Files.writeString(fakeAudio, "오디오가 아닌 텍스트");

        GeneralException exception = assertThrows(GeneralException.class,
                () -> audioDurationReader.readSeconds(fakeAudio.toString()));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
    }

    @Test
    void readSeconds_브라우저로_녹음한_AAC_MP4는_조각을_더해_재생시간을_읽는다() throws Exception {
        assertEquals(16, audioDurationReader.readSeconds(resource(CHROME_AAC)));
    }

    @Test
    void readSeconds_브라우저로_녹음한_Opus_MP4도_재생시간을_읽는다() throws Exception {
        assertEquals(16, audioDurationReader.readSeconds(resource(CHROME_OPUS)));
    }

    @Test
    void readSeconds_WebM은_m4a로_이름을_바꿔도_지원하지_않는_포맷() throws Exception {
        Path renamed = Files.copy(Path.of(resource(CHROME_WEBM)), tempDir.resolve("recording.m4a"));

        GeneralException exception = assertThrows(GeneralException.class,
                () -> audioDurationReader.readSeconds(renamed.toString()));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
    }

    private String resource(String name) throws Exception {
        return Path.of(getClass().getClassLoader().getResource(name).toURI()).toString();
    }
}
