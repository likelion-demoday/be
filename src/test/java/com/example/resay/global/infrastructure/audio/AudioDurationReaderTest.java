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
}
