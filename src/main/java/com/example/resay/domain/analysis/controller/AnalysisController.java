package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.code.AnalysisSuccessCode;
import com.example.resay.domain.analysis.dto.AnalysisReportResponseDto;
import com.example.resay.domain.analysis.service.AnalysisReportQueryService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "대화 분석 상태 및 보고서 API")
public class AnalysisController {

    private final AnalysisReportQueryService analysisReportQueryService;

    @GetMapping("/{recordingId}")
    @Operation(
            summary = "분석 상태 및 보고서 조회",
            description = "본인이 소유한 녹음의 분석 상태를 조회합니다. "
                    + "분석이 완료된 경우에만 보고서를 반환합니다."
    )
    public ResponseEntity<ApiResponse<AnalysisReportResponseDto>> get(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "조회할 녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        AnalysisReportResponseDto response =
                analysisReportQueryService.getByRecordingId(recordingId, userId);
        return ResponseEntity.status(AnalysisSuccessCode.REPORT_GET.getHttpStatus())
                .body(ApiResponse.onSuccess(AnalysisSuccessCode.REPORT_GET, response));
    }
}
