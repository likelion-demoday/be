package com.example.resay.domain.recording.controller;

import com.example.resay.domain.recording.dto.RecordingTypeSelectRequestDto;
import com.example.resay.domain.recording.dto.RecordingUploadResponseDto;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<RecordingUploadResponseDto>> upload(
            @CurrentUserId Long userId,
            @RequestPart("audioFile") MultipartFile audioFile
    ) {
        RecordingUploadResponseDto response = recordingService.upload(userId, audioFile);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.onSuccess("녹음 업로드 성공", response));
    }

    @PatchMapping("/{recordingId}/type")
    public ResponseEntity<ApiResponse<Void>> selectType(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId,
            @RequestBody RecordingTypeSelectRequestDto request
    ) {
        recordingService.selectType(recordingId, userId, request.relationshipType());
        return ResponseEntity.ok(ApiResponse.onSuccess("관계유형 선택 성공"));
    }
}