package com.example.resay.global.apiPayload.code.status;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400_1", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401_1", "인증되지 않았습니다."),
    // 프론트가 토큰 재발급·재로그인을 판단할 수 있도록 토큰 없음(401_1)과 구분한다
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "COMMON401_2", "유효하지 않거나 만료된 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403_1", "접근이 금지되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404_1", "해당 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "COMMON405_1",
            "지원하지 않는 HTTP 메서드입니다."
    ),
    NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE,
            "COMMON406_1",
            "허용할 수 없는 응답 형식입니다."
    ),
    PAYLOAD_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "COMMON413_1",
            "요청 데이터의 크기가 허용 범위를 초과했습니다."
    ),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "COMMON415_1",
            "지원하지 않는 미디어 타입입니다."
    ),
    // 로그인 · 가입 시도 횟수 제한. 다시 시도할 수 있는 시간은 Retry-After 헤더와 error.retryAfterSeconds로 알려준다
    TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "COMMON429_1",
            "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON500_1",
            "서버 내부 오류가 발생했습니다."
    ),
    SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "COMMON503_1",
            "현재 서비스를 이용할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public static GeneralErrorCode from(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_SERVER_ERROR;
        };
    }
}
