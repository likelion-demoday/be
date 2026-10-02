package com.example.resay.global.infrastructure.audio;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class AudioDurationReader {

    // 손상되었거나 확장자만 바꾼 파일은 재생시간을 읽지 못하므로 지원하지 않는 포맷으로 처리한다
    public int readSeconds(String filePath) {
        try {
            return AudioFileIO.read(new File(filePath)).getAudioHeader().getTrackLength();
        } catch (Exception e) {
            throw new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
