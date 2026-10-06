package com.example.resay.domain.recording.controller;

import com.example.resay.domain.recording.code.RecordingSuccessCode;
import com.example.resay.domain.recording.dto.RecordingTypeSelectRequestDto;
import com.example.resay.domain.recording.dto.RecordingStatusResponseDto;
import com.example.resay.domain.recording.dto.RecordingUploadResponseDto;
import com.example.resay.domain.recording.dto.SpeakerMappingRequestDto;
import com.example.resay.domain.recording.dto.SpeakerSamplesResponseDto;
import com.example.resay.domain.recording.service.RecordingAudioService;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.service.RecordingStatusService;
import com.example.resay.domain.transcription.service.SpeakerMappingService;
import com.example.resay.domain.transcription.service.SpeakerSampleService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.AudioUrlSigner;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;
    private final SpeakerMappingService speakerMappingService;
    private final RecordingStatusService recordingStatusService;
    private final SpeakerSampleService speakerSampleService;
    private final RecordingAudioService recordingAudioService;
    private final AudioUrlSigner audioUrlSigner;

    // 화자 선택 화면: 화자별로 들려줄 구간과 재생 주소(10분 후 만료)를 준다
    @GetMapping("/{recordingId}/speaker-samples")
    public ResponseEntity<ApiResponse<SpeakerSamplesResponseDto>> getSpeakerSamples(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId
    ) {
        var speakers = speakerSampleService.getSamples(recordingId, userId);
        AudioUrlSigner.SignedAudioUrl signed = audioUrlSigner.sign(recordingId);
        String audioUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/recordings/{recordingId}/audio")
                .queryParam("expires", signed.expires())
                .queryParam("signature", signed.signature())
                .buildAndExpand(recordingId)
                .toUriString();
        return ResponseEntity.status(RecordingSuccessCode.GET_SPEAKER_SAMPLES.getHttpStatus())
                .body(ApiResponse.onSuccess(RecordingSuccessCode.GET_SPEAKER_SAMPLES,
                        new SpeakerSamplesResponseDto(audioUrl, speakers)));
    }

    // <audio src>로 재생하므로 로그인 토큰 대신 서명된 임시 주소로 확인한다 (Range 요청 지원)
    @GetMapping("/{recordingId}/audio")
    public ResponseEntity<Resource> getAudio(
            @PathVariable Long recordingId,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        RecordingAudioService.AudioFile audio = recordingAudioService.getAudio(recordingId, expires, signature);
        return ResponseEntity.ok()
                .contentType(audio.mediaType())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(audio.resource());
    }

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

    // 대기 화면에서 주기적으로 조회해 다음 화면(화자 선택, 보고서, 실패 안내)으로 넘어갈 시점을 안다
    @GetMapping("/{recordingId}/status")
    public ResponseEntity<ApiResponse<RecordingStatusResponseDto>> getStatus(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId
    ) {
        return ResponseEntity.status(RecordingSuccessCode.GET_STATUS.getHttpStatus())
                .body(ApiResponse.onSuccess(RecordingSuccessCode.GET_STATUS,
                        recordingStatusService.getStatus(recordingId, userId)));
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
