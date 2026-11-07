package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.character.model.CharacterDesignSpec;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.port.CharacterDesignGenerator;
import com.example.resay.domain.character.service.CharacterDesignGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class LinerCharacterDesignGenerator implements CharacterDesignGenerator {

    private final LinerApiClient apiClient;
    private final LinerCharacterDesignRequestFactory requestFactory;
    private final ObjectMapper objectMapper;

    public LinerCharacterDesignGenerator(
            LinerApiClient apiClient,
            LinerCharacterDesignRequestFactory requestFactory,
            ObjectMapper objectMapper
    ) {
        this.apiClient = apiClient;
        this.requestFactory = requestFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public CharacterDesignSpec generate(CharacterImageGenerationCommand command) {
        try {
            return generateOnce(command, false);
        } catch (CharacterDesignGenerationException firstFailure) {
            if (!isRetryableValidationFailure(firstFailure)) {
                throw firstFailure;
            }
            log.warn(
                    "캐릭터 디자인 응답 검증 실패로 1회 재시도: scenario={}, speakerRole={}, errorCode={}",
                    command.scenario(),
                    command.speakerRole(),
                    firstFailure.getErrorCode()
            );
            try {
                return generateOnce(command, true);
            } catch (CharacterDesignGenerationException secondFailure) {
                secondFailure.addSuppressed(firstFailure);
                throw secondFailure;
            }
        }
    }

    private CharacterDesignSpec generateOnce(
            CharacterImageGenerationCommand command,
            boolean retry
    ) {
        LinerChatResult result;
        try {
            result = apiClient.chat(
                    retry ? requestFactory.createRetry(command) : requestFactory.create(command)
            );
        } catch (LinerApiException exception) {
            throw new CharacterDesignGenerationException(
                    exception.getErrorCode(),
                    "캐릭터 디자인 서비스 처리에 실패했습니다.",
                    exception
            );
        }
        validateCompletion(result);
        CharacterDesignSpec design = parse(result.content());
        if (!command.characterName().equals(design.characterName())) {
            return design.withCharacterName(command.characterName());
        }
        return design;
    }

    private void validateCompletion(LinerChatResult result) {
        if (result == null
                || result.response() == null
                || result.response().choices() == null
                || result.response().choices().isEmpty()
                || result.response().choices().get(0) == null
                || result.response().choices().get(0).message() == null) {
            throw new CharacterDesignGenerationException(
                    "invalid_character_design_response",
                    "캐릭터 디자인 응답 구조가 올바르지 않습니다.",
                    null
            );
        }
        String finishReason = result.response().choices().get(0).finishReason();
        if (!"stop".equals(finishReason)) {
            throw new CharacterDesignGenerationException(
                    "incomplete_character_design",
                    "캐릭터 디자인 응답이 정상적으로 완료되지 않았습니다.",
                    null
            );
        }
        if (!StringUtils.hasText(result.response().model())) {
            throw new CharacterDesignGenerationException(
                    "invalid_character_design_response",
                    "캐릭터 디자인 응답에 모델 정보가 없습니다.",
                    null
            );
        }
    }

    private CharacterDesignSpec parse(String content) {
        try {
            return objectMapper.readValue(content, CharacterDesignSpec.class);
        } catch (Exception exception) {
            throw new CharacterDesignGenerationException(
                    "invalid_character_design_response",
                    "캐릭터 디자인 응답을 해석할 수 없습니다.",
                    exception
            );
        }
    }

    private boolean isRetryableValidationFailure(CharacterDesignGenerationException exception) {
        return !(exception.getCause() instanceof LinerApiException)
                && ("invalid_character_design_response".equals(exception.getErrorCode())
                || "incomplete_character_design".equals(exception.getErrorCode()));
    }
}
