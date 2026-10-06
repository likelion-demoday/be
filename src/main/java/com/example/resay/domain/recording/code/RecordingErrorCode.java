package com.example.resay.domain.recording.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordingErrorCode implements BaseErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "RECORDING400_1", "잘못된 녹음 요청입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "RECORDING400_2", "현재 상태에서는 처리할 수 없는 요청입니다."),
    DURATION_TOO_SHORT(HttpStatus.BAD_REQUEST, "RECORDING400_3", "녹음 길이가 너무 짧습니다. 5분 이상이어야 합니다."),
    INVALID_SPEAKER_MAPPING(HttpStatus.BAD_REQUEST, "RECORDING400_4", "선택한 화자 정보가 올바르지 않습니다."),
    TRANSCRIPTION_NOT_COMPLETED(HttpStatus.CONFLICT, "RECORDING409_1", "전사가 끝난 녹음만 화자를 지정할 수 있습니다."),
    DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "RECORDING409_2", "분석이 끝난 대화만 삭제할 수 있습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "RECORDING415_1", "지원하지 않는 오디오 포맷입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "RECORDING413_1", "파일 용량이 허용 범위를 초과했습니다."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORDING404_1", "해당 녹음을 찾을 수 없습니다."),
    INVALID_AUDIO_URL(HttpStatus.FORBIDDEN, "RECORDING403_1", "재생 주소가 만료되었거나 올바르지 않습니다."),
    AUDIO_DELETED(HttpStatus.GONE, "RECORDING410_1", "보관 기간이 지나 음성이 삭제되었습니다."),
    DURATION_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "RECORDING413_2", "녹음 길이가 너무 깁니다. 30분 이하여야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
