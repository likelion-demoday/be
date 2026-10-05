package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.transcription.code.TranscriptionSuccessCode;
import com.example.resay.domain.transcription.service.TranscriptionService;
import com.example.resay.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 외부 전사 서비스(CLOVA)가 호출하는 API라 로그인 없이 열려 있다
// 주소의 비밀값으로 우리가 요청한 작업인지 확인한다
@Hidden
@RestController
@RequestMapping("/api/v1/transcriptions/callback")
@RequiredArgsConstructor
public class TranscriptionCallbackController {

    private final TranscriptionService transcriptionService;

    @PostMapping("/{callbackSecret}")
    public ResponseEntity<ApiResponse<Void>> receive(
            @PathVariable String callbackSecret,
            @RequestBody String body
    ) {
        transcriptionService.receiveResult(callbackSecret, body);
        return ResponseEntity.status(TranscriptionSuccessCode.CALLBACK_RECEIVED.getHttpStatus())
                .body(ApiResponse.onSuccess(TranscriptionSuccessCode.CALLBACK_RECEIVED));
    }
}
