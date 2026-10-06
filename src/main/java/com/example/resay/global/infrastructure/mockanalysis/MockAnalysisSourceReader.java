package com.example.resay.global.infrastructure.mockanalysis;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Profile("mock")
@Component
public class MockAnalysisSourceReader implements AnalysisSourceReader {

    private static final String RESOURCE_DIRECTORY = "mock/analysis/";

    private final ObjectMapper objectMapper;
    private final AtomicLong recordingSequence = new AtomicLong(System.currentTimeMillis());
    private final Map<Long, AnalysisSource> sources = new ConcurrentHashMap<>();

    public MockAnalysisSourceReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisSource create(AnalysisScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("분석 시나리오는 비어 있을 수 없습니다.");
        }

        AnalysisSource template = readTemplate(resourceName(scenario));
        Long recordingId = recordingSequence.incrementAndGet();
        AnalysisSource source = new AnalysisSource(
                recordingId,
                template.scenario(),
                template.durationMs(),
                template.speakers(),
                template.segments()
        );
        sources.put(recordingId, source);
        return source;
    }

    @Override
    public AnalysisSource read(Long recordingId) {
        AnalysisSource source = sources.get(recordingId);
        if (source == null) {
            throw new IllegalArgumentException("등록되지 않은 목업 녹음입니다.");
        }
        return source;
    }

    private AnalysisSource readTemplate(String resourceName) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_DIRECTORY + resourceName);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, AnalysisSource.class);
        } catch (Exception exception) {
            throw new IllegalStateException("목업 전사 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private String resourceName(AnalysisScenario scenario) {
        return switch (scenario) {
            case FRIEND_DAILY -> "friend-daily.json";
            case COUPLE_DAILY -> "couple-daily.json";
            case COUPLE_CONFLICT -> "couple-conflict.json";
            case PARENT_CHILD_CONFLICT -> "parent-child-conflict.json";
        };
    }
}
