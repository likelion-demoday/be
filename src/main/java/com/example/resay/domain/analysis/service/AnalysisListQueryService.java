package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisListItemDto;
import com.example.resay.domain.analysis.dto.AnalysisListResponseDto;
import com.example.resay.domain.analysis.repository.AnalysisListItemProjection;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisListQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationAnalysisRepository conversationAnalysisRepository;

    public AnalysisListResponseDto getList(Long userId, int page, int size) {
        validatePage(page, size);
        Page<AnalysisListItemProjection> result = conversationAnalysisRepository
                .findPageByUserId(userId, PageRequest.of(page, size));
        List<AnalysisListItemDto> items = result.getContent().stream()
                .map(this::toDto)
                .toList();

        return new AnalysisListResponseDto(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
    }

    private AnalysisListItemDto toDto(AnalysisListItemProjection source) {
        return new AnalysisListItemDto(
                source.getRecordingId(),
                source.getTitle(),
                source.getRelationshipType(),
                source.getDurationSeconds(),
                source.getCreatedAt(),
                source.getAnalysisStatus(),
                source.getFailureReason()
        );
    }
}
