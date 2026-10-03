package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.dto.AnalysisReportResponseDto;
import com.example.resay.domain.analysis.dto.MockAnalysisRequestDto;
import com.example.resay.domain.analysis.service.MockAnalysisApplicationService;
import com.example.resay.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "mock"})
@RestController
@RequestMapping("/api/v1/mock/analyses")
@RequiredArgsConstructor
public class MockAnalysisController {

    private final MockAnalysisApplicationService mockAnalysisApplicationService;

    @PostMapping
    public ApiResponse<AnalysisReportResponseDto> create(
            @Valid @RequestBody MockAnalysisRequestDto request
    ) {
        AnalysisReportResponseDto response = mockAnalysisApplicationService.create(request.scenario());
        return ApiResponse.onSuccess("목업 분석 보고서가 생성되었습니다.", response);
    }

    @GetMapping("/{recordingId}")
    public ApiResponse<AnalysisReportResponseDto> get(@PathVariable Long recordingId) {
        AnalysisReportResponseDto response = mockAnalysisApplicationService.get(recordingId);
        return ApiResponse.onSuccess("목업 분석 보고서를 조회했습니다.", response);
    }
}
