package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisReportResponseDto;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.global.infrastructure.mockanalysis.MockAnalysisSourceReader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("mock")
@Service
@RequiredArgsConstructor
public class MockAnalysisApplicationService {

    private final MockAnalysisSourceReader mockAnalysisSourceReader;
    private final AnalysisProcessor analysisProcessor;
    private final AnalysisReportQueryService analysisReportQueryService;

    public AnalysisReportResponseDto create(AnalysisScenario scenario) {
        AnalysisSource source = mockAnalysisSourceReader.create(scenario);
        analysisProcessor.process(source.recordingId());
        return analysisReportQueryService.getByRecordingIdForInternalUse(source.recordingId());
    }

    public AnalysisReportResponseDto get(Long recordingId) {
        mockAnalysisSourceReader.read(recordingId);
        return analysisReportQueryService.getByRecordingIdForInternalUse(recordingId);
    }
}
