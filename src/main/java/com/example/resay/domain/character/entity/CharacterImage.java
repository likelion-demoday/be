package com.example.resay.domain.character.entity;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@Entity
@Table(
        name = "character_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_character_images_analysis_speaker",
                columnNames = {"analysis_id", "speaker_role"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterImage extends BaseEntity {

    private static final int OBJECT_KEY_MAX_LENGTH = 500;
    private static final int MEDIA_TYPE_MAX_LENGTH = 100;
    private static final int MODEL_NAME_MAX_LENGTH = 100;
    private static final int VERSION_MAX_LENGTH = 50;
    private static final int FAILURE_CODE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker_role", nullable = false, updatable = false, columnDefinition = "varchar(20)")
    private SpeakerRole speakerRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private CharacterImageStatus status;

    @Column(name = "object_key", length = OBJECT_KEY_MAX_LENGTH)
    private String objectKey;

    @Column(name = "media_type", length = MEDIA_TYPE_MAX_LENGTH)
    private String mediaType;

    @Column(name = "model_name", length = MODEL_NAME_MAX_LENGTH)
    private String modelName;

    @Column(name = "prompt_version", length = VERSION_MAX_LENGTH)
    private String promptVersion;

    @Column(name = "failure_code", length = FAILURE_CODE_MAX_LENGTH)
    private String failureCode;

    private CharacterImage(Long analysisId, SpeakerRole speakerRole) {
        this.analysisId = analysisId;
        this.speakerRole = speakerRole;
        this.status = CharacterImageStatus.PENDING;
    }

    public static CharacterImage prepare(Long analysisId, SpeakerRole speakerRole) {
        if (analysisId == null || analysisId <= 0) {
            throw new IllegalArgumentException("analysisId는 양수여야 합니다.");
        }
        if (speakerRole == null) {
            throw new IllegalArgumentException("speakerRole은 비어 있을 수 없습니다.");
        }
        return new CharacterImage(analysisId, speakerRole);
    }

    public void start() {
        requireStatus(CharacterImageStatus.PENDING);
        status = CharacterImageStatus.GENERATING;
    }

    public void retry() {
        requireStatus(CharacterImageStatus.FAILED);
        this.objectKey = null;
        this.mediaType = null;
        this.modelName = null;
        this.promptVersion = null;
        this.failureCode = null;
        this.status = CharacterImageStatus.PENDING;
    }

    public void complete(
            String objectKey,
            String mediaType,
            String modelName,
            String promptVersion
    ) {
        requireStatus(CharacterImageStatus.GENERATING);
        requireText(objectKey, "objectKey");
        requireText(mediaType, "mediaType");
        requireText(modelName, "modelName");
        requireText(promptVersion, "promptVersion");
        requireMaxLength(objectKey, "objectKey", OBJECT_KEY_MAX_LENGTH);
        requireMaxLength(mediaType, "mediaType", MEDIA_TYPE_MAX_LENGTH);
        requireMaxLength(modelName, "modelName", MODEL_NAME_MAX_LENGTH);
        requireMaxLength(promptVersion, "promptVersion", VERSION_MAX_LENGTH);

        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.failureCode = null;
        this.status = CharacterImageStatus.COMPLETED;
    }

    public void fail(String failureCode) {
        requireStatus(CharacterImageStatus.GENERATING);
        requireText(failureCode, "failureCode");
        requireMaxLength(failureCode, "failureCode", FAILURE_CODE_MAX_LENGTH);
        this.failureCode = failureCode;
        this.status = CharacterImageStatus.FAILED;
    }

    private void requireStatus(CharacterImageStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(expected + " 상태에서만 변경할 수 있습니다.");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }

    private static void requireMaxLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "의 길이가 너무 깁니다.");
        }
    }
}
