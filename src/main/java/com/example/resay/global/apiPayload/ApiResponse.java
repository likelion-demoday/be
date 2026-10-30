package com.example.resay.global.apiPayload;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import com.example.resay.global.apiPayload.code.status.GeneralSuccessCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"isSuccess", "code", "message", "result", "error"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    private final String code;
    private final String message;
    private final T result;
    private final Object error;

    public static <T> ApiResponse<T> onSuccess(String message, T result) {
        return onSuccess(GeneralSuccessCode.OK, message, result);
    }

    public static ApiResponse<Void> onSuccess(String message) {
        return onSuccess(GeneralSuccessCode.OK, message, null);
    }

    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode successCode, T result) {
        return onSuccess(successCode, successCode.getMessage(), result);
    }

    public static ApiResponse<Void> onSuccess(BaseSuccessCode successCode) {
        return onSuccess(successCode, successCode.getMessage(), null);
    }

    private static <T> ApiResponse<T> onSuccess(
            BaseSuccessCode successCode,
            String message,
            T result
    ) {
        return new ApiResponse<>(true, successCode.getCode(), message, result, null);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode, Object error) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, error);
    }
}
