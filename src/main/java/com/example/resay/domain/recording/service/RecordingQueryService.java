package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.RecordingDetailResponseDto;
import com.example.resay.domain.recording.dto.RecordingListItemDto;
import com.example.resay.domain.recording.dto.RecordingListResponseDto;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordingQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RecordingRepository recordingRepository;

    // 대화 보관함: 분석이 끝난 대화만 최신순으로 보여준다 (사용자가 지운 대화는 빠진다)
    public RecordingListResponseDto getList(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        Page<Recording> result = recordingRepository.findArchivedPage(userId, PageRequest.of(page, size));
        return new RecordingListResponseDto(
                result.getContent().stream().map(RecordingListItemDto::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    // 대화 보관함 상세 (다시 듣기). 목록에 보이는 대화만 연다
    public RecordingDetailResponseDto getDetail(Long recordingId, Long userId, String audioUrl) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted() && found.getStatus() == RecordingStatus.COMPLETED)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        return RecordingDetailResponseDto.of(recording, audioUrl);
    }
}
