package com.example.resay.global.infrastructure.audio;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

@Component
public class AudioDurationReader {

    // 손상되었거나 확장자만 바꾼 파일은 재생시간을 읽지 못하므로 지원하지 않는 포맷으로 처리한다
    public int readSeconds(String filePath) {
        int seconds = readHeaderSeconds(filePath);
        if (seconds > 0) {
            return seconds;
        }
        // 브라우저 녹음 MP4는 헤더의 재생시간이 0이고, Opus 코덱이면 헤더 읽기 자체가 실패한다
        // 삼성 통화 녹음은 전용 메타데이터(meta) 때문에 태그 읽기에서 실패한다
        return readMp4Seconds(filePath);
    }

    private int readHeaderSeconds(String filePath) {
        try {
            return AudioFileIO.read(new File(filePath)).getAudioHeader().getTrackLength();
        } catch (Exception e) {
            return 0;
        }
    }

    private int readMp4Seconds(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Mp4DurationReader.isMp4(path)) {
                throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            long millis = Mp4DurationReader.readMillis(path);
            if (millis <= 0) {
                throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            return (int) Math.round(millis / 1000.0);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
