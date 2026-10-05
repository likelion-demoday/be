package com.example.resay.domain.recording.controller;

import com.example.resay.domain.recording.code.RecordingSuccessCode;
import com.example.resay.domain.recording.dto.RecordingTypeSelectRequestDto;
import com.example.resay.domain.recording.dto.RecordingUploadResponseDto;
import com.example.resay.domain.recording.dto.SpeakerMappingRequestDto;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.service.SpeakerMappingService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;
    private final SpeakerMappingService speakerMappingService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<RecordingUploadResponseDto>> upload(
            @CurrentUserId Long userId,
            @RequestPart("audioFile") MultipartFile audioFile
    ) {
        RecordingUploadResponseDto response = recordingService.upload(userId, audioFile);
        return ResponseEntity.status(RecordingSuccessCode.UPLOAD.getHttpStatus())
                .body(ApiResponse.onSuccess(RecordingSuccessCode.UPLOAD, response));
    }

    @PatchMapping("/{recordingId}/type")
    public ResponseEntity<ApiResponse<Void>> selectType(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId,
            @RequestBody RecordingTypeSelectRequestDto request
    ) {
        recordingService.selectType(recordingId, userId, request.relationshipType());
        return ResponseEntity.status(RecordingSuccessCode.SELECT_TYPE.getHttpStatus())
                .body(ApiResponse.onSuccess(RecordingSuccessCode.SELECT_TYPE));
    }

    // 전사가 끝난 뒤 본인 화자·상대 닉네임을 저장하면 분석이 시작된다
    @PostMapping("/{recordingId}/speaker-mapping")
    public ResponseEntity<ApiResponse<Void>> mapSpeakers(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId,
            @Valid @RequestBody SpeakerMappingRequestDto request
    ) {
        speakerMappingService.map(recordingId, userId, request);
        return ResponseEntity.status(RecordingSuccessCode.MAP_SPEAKERS.getHttpStatus())
                .body(ApiResponse.onSuccess(RecordingSuccessCode.MAP_SPEAKERS));
    }
}
