package com.example.resay.domain.character.controller;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.code.CharacterImageSuccessCode;
import com.example.resay.domain.character.dto.CharacterImageListResponseDto;
import com.example.resay.domain.character.dto.CharacterImageRequestResponseDto;
import com.example.resay.domain.character.service.CharacterImageQueryService;
import com.example.resay.domain.character.service.CharacterImageRequestService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "Character Image", description = "분석 캐릭터 이미지 API")
@ConditionalOnProperty(
        prefix = "character.image",
        name = "generation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CharacterImageController {

    private final CharacterImageRequestService characterImageRequestService;
    private final CharacterImageQueryService characterImageQueryService;

    @GetMapping("/{recordingId}/character-images")
    @Operation(
            summary = "캐릭터 이미지 상태 조회",
            description = "화자별 캐릭터 이미지 생성 상태와 완료된 이미지의 조회 주소를 반환합니다."
    )
    public ResponseEntity<ApiResponse<CharacterImageListResponseDto>> getAll(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        CharacterImageListResponseDto response =
                characterImageQueryService.getAll(userId, recordingId);
        return ResponseEntity.status(CharacterImageSuccessCode.LIST_GET.getHttpStatus())
                .body(ApiResponse.onSuccess(CharacterImageSuccessCode.LIST_GET, response));
    }

    @GetMapping("/{recordingId}/character-images/{speakerRole}/content")
    @Operation(
            summary = "캐릭터 이미지 파일 조회",
            description = "생성이 완료된 화자의 캐릭터 이미지 파일을 반환합니다."
    )
    public ResponseEntity<byte[]> getContent(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "녹음 ID", example = "1")
            @PathVariable Long recordingId,
            @Parameter(description = "화자 역할", example = "SELF")
            @PathVariable SpeakerRole speakerRole
    ) {
        CharacterImageQueryService.CharacterImageContent image =
                characterImageQueryService.getContent(userId, recordingId, speakerRole);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(image.content());
    }

    @PostMapping("/{recordingId}/character-images")
    @Operation(
            summary = "캐릭터 이미지 생성 요청",
            description = "완료된 분석의 캐릭터 이미지 생성을 요청합니다. "
                    + "요청이 접수되면 크레딧을 차감하고 비동기로 이미지를 생성합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "생성 요청 접수"
            )
    )
    public ResponseEntity<ApiResponse<CharacterImageRequestResponseDto>> request(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "분석이 완료된 녹음 ID", example = "1")
            @PathVariable Long recordingId
    ) {
        CharacterImageRequestResponseDto response =
                characterImageRequestService.request(userId, recordingId);
        return ResponseEntity.status(CharacterImageSuccessCode.REQUESTED.getHttpStatus())
                .body(ApiResponse.onSuccess(CharacterImageSuccessCode.REQUESTED, response));
    }
}
