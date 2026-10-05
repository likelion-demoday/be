package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "분석 상태 및 보고서 응답")
public record AnalysisReportResponseDto(
        @Schema(description = "녹음 ID", example = "1")
        Long recordingId,
        @Schema(description = "분석 상태", example = "COMPLETED")
        AnalysisStatus status,
        @Schema(description = "분석 실패 사유. 실패 상태가 아니면 null", example = "INSUFFICIENT_SPEAKER_DATA")
        AnalysisFailureReason failureReason,
        @Schema(description = "완료된 분석 보고서. 분석 중이거나 실패한 경우 null")
        AnalysisReportDto report,
        @Schema(description = "분석 모델명. 분석 완료 전에는 null", example = "liner-mark-1.1")
        String modelName,
        @Schema(description = "분석 프롬프트 버전. 분석 완료 전에는 null", example = "analysis-prompt-v4")
        String promptVersion,
        @Schema(description = "보고서 스키마 버전. 분석 완료 전에는 null", example = "analysis-report-v4")
        String schemaVersion
) {
}
