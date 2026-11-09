package com.example.resay.domain.share.controller;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.service.CharacterImageQueryService;
import com.example.resay.domain.share.code.ShareSuccessCode;
import com.example.resay.domain.share.dto.SharedAnalysisResponseDto;
import com.example.resay.domain.share.service.SharedAnalysisService;
import com.example.resay.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shared")
@RequiredArgsConstructor
@Tag(name = "Shared Analysis", description = "로그인 없이 공유 토큰으로 조회하는 보고서 API")
public class SharedAnalysisController {

    private final SharedAnalysisService sharedAnalysisService;

    @GetMapping("/analysis-report")
    @Operation(summary = "공유 분석 보고서 조회")
    public ResponseEntity<ApiResponse<SharedAnalysisResponseDto>> getReport(
            @Parameter(
                    description = "Share {공유 토큰}",
                    example = "Share AbCdEf0123456789_-AbCdEf0123456789_-AbCdEf0"
            )
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorizationHeader
    ) {
        SharedAnalysisResponseDto response = sharedAnalysisService.getReport(
                authorizationHeader
        );
        return ResponseEntity.status(ShareSuccessCode.REPORT_GET.getHttpStatus())
                .body(ApiResponse.onSuccess(ShareSuccessCode.REPORT_GET, response));
    }

    @GetMapping("/character-image")
    @Operation(summary = "공유 보고서 캐릭터 이미지 조회")
    public ResponseEntity<byte[]> getCharacterImage(
            @Parameter(
                    description = "Share {공유 토큰}",
                    example = "Share AbCdEf0123456789_-AbCdEf0123456789_-AbCdEf0"
            )
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorizationHeader,
            @Parameter(description = "조회할 캐릭터의 화자 역할", example = "SELF")
            @RequestParam SpeakerRole speakerRole
    ) {
        CharacterImageQueryService.CharacterImageContent image =
                sharedAnalysisService.getCharacterImage(authorizationHeader, speakerRole);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .body(image.content());
    }
}
