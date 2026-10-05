package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.code.AnalysisSuccessCode;
import com.example.resay.domain.analysis.dto.AnalysisListResponseDto;
import com.example.resay.domain.analysis.dto.AnalysisReportResponseDto;
import com.example.resay.domain.analysis.service.AnalysisDeletionService;
import com.example.resay.domain.analysis.service.AnalysisListQueryService;
import com.example.resay.domain.analysis.service.AnalysisReportQueryService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "대화 분석 상태 및 보고서 API")
public class AnalysisController {

    private final AnalysisReportQueryService analysisReportQueryService;
    private final AnalysisListQueryService analysisListQueryService;
    private final AnalysisDeletionService analysisDeletionService;

    @GetMapping
    @Operation(
            summary = "분석 목록 조회",
            description = "로그인 사용자의 분석 목록을 최신순으로 조회합니다."
    )
    public ResponseEntity<ApiResponse<AnalysisListResponseDto>> getList(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 1 이상 50 이하", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        AnalysisListResponseDto response = analysisListQueryService.getList(userId, page, size);
        return ResponseEntity.status(AnalysisSuccessCode.LIST_GET.getHttpStatus())
                .body(ApiResponse.onSuccess(AnalysisSuccessCode.LIST_GET, response));
    }

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

    @DeleteMapping("/{recordingId}")
    @Operation(
            summary = "분석 결과 삭제",
            description = "본인이 소유한 완료 또는 실패 분석 결과를 삭제합니다. "
                    + "녹음과 전사 데이터는 삭제하지 않습니다."
    )
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "삭제할 분석의 녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        analysisDeletionService.delete(recordingId, userId);
        return ResponseEntity.status(AnalysisSuccessCode.DELETE.getHttpStatus())
                .body(ApiResponse.onSuccess(AnalysisSuccessCode.DELETE));
    }
}
