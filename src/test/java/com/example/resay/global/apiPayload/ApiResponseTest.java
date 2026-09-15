package com.example.resay.global.apiPayload;

import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import com.example.resay.global.apiPayload.code.status.GeneralSuccessCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void onSuccessCreatesResponseWithResult() {
        Map<String, Long> result = Map.of("id", 1L);

        ApiResponse<Map<String, Long>> response = ApiResponse.onSuccess("조회 성공", result);

        assertTrue(response.getIsSuccess());
        assertEquals(GeneralSuccessCode.OK.getCode(), response.getCode());
        assertEquals("조회 성공", response.getMessage());
        assertSame(result, response.getResult());
        assertNull(response.getError());
    }

    @Test
    void onFailureCreatesResponseWithErrorDetails() {
        Map<String, String> errors = Map.of("name", "이름은 필수입니다.");

        ApiResponse<Void> response = ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST, errors);

        assertFalse(response.getIsSuccess());
        assertEquals(GeneralErrorCode.BAD_REQUEST.getCode(), response.getCode());
        assertEquals(GeneralErrorCode.BAD_REQUEST.getMessage(), response.getMessage());
        assertNull(response.getResult());
        assertSame(errors, response.getError());
    }

    @Test
    void serializesUsingCommonResponseContract() {
        ApiResponse<String> response = ApiResponse.onSuccess("조회 성공", "result-value");

        String json = JsonMapper.builder().build().writeValueAsString(response);

        assertEquals(
                "{\"isSuccess\":true,\"code\":\"COMMON200\",\"message\":\"조회 성공\","
                        + "\"result\":\"result-value\",\"error\":null}",
                json
        );
    }
}
