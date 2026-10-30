package com.example.resay.global.exception;

import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handlesGeneralExceptionWithDefinedStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void convertsValidationErrorsToFieldMap() throws Exception {
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.error.name").value("이름은 필수입니다."));
    }

    @Test
    void handlesMalformedJsonAsBadRequest() throws Exception {
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void handlesUnsupportedMethodAsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/test"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.METHOD_NOT_ALLOWED.getCode()));
    }

    @Test
    void handlesAccessDeniedExceptionAsForbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromResponse() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value(GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @PostMapping
        ApiResponse<Void> validate(@Valid @RequestBody TestRequest request) {
            return ApiResponse.onSuccess("검증 성공");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new GeneralException(GeneralErrorCode.NOT_FOUND);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("internal-secret");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal-secret");
        }
    }

    private record TestRequest(
            @NotBlank(message = "이름은 필수입니다.") String name
    ) {
    }
}
