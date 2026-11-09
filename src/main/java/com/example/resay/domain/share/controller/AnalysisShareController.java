package com.example.resay.domain.share.controller;

import com.example.resay.domain.share.code.ShareSuccessCode;
import com.example.resay.domain.share.dto.ShareLinkResponseDto;
import com.example.resay.domain.share.service.SharedAnalysisService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "Analysis Share", description = "분석 보고서 공유 링크 관리 API")
public class AnalysisShareController {

    private final SharedAnalysisService sharedAnalysisService;

    @PostMapping("/{recordingId}/shares")
    @Operation(
            summary = "분석 보고서 공유 링크 발급",
            description = "본인이 소유한 완료 보고서의 7일 유효 공유 링크를 발급합니다. "
                    + "재발급하면 이전 링크는 즉시 무효화됩니다."
    )
    public ResponseEntity<ApiResponse<ShareLinkResponseDto>> issue(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "공유할 녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        ShareLinkResponseDto response = sharedAnalysisService.issue(recordingId, userId);
        return ResponseEntity.status(ShareSuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.onSuccess(ShareSuccessCode.CREATED, response));
    }

    @DeleteMapping("/{recordingId}/shares")
    @Operation(summary = "분석 보고서 공유 취소")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "공유를 취소할 녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        sharedAnalysisService.cancel(recordingId, userId);
        return ResponseEntity.status(ShareSuccessCode.DELETED.getHttpStatus())
                .body(ApiResponse.onSuccess(ShareSuccessCode.DELETED));
    }
}
