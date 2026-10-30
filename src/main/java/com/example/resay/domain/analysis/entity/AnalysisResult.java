package com.example.resay.domain.analysis.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@Entity
@Table(
        name = "analysis_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_results_analysis_id",
                columnNames = "analysis_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseEntity {

    private static final int MODEL_NAME_MAX_LENGTH = 100;
    private static final int VERSION_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private Long analysisId;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "longtext")
    private String resultJson;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false, length = 50)
    private String schemaVersion;

    private AnalysisResult(
            Long analysisId,
            String resultJson,
            String modelName,
            String promptVersion,
            String schemaVersion
    ) {
        this.analysisId = analysisId;
        this.resultJson = resultJson;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
    }

    public static AnalysisResult create(
            Long analysisId,
            String resultJson,
            String modelName,
            String promptVersion,
            String schemaVersion
    ) {
        if (analysisId == null || analysisId <= 0) {
            throw new IllegalArgumentException("analysisId는 양수여야 합니다.");
        }
        requireText(resultJson, "resultJson");
        requireText(modelName, "modelName");
        requireText(promptVersion, "promptVersion");
        requireText(schemaVersion, "schemaVersion");
        requireMaxLength(modelName, "modelName", MODEL_NAME_MAX_LENGTH);
        requireMaxLength(promptVersion, "promptVersion", VERSION_MAX_LENGTH);
        requireMaxLength(schemaVersion, "schemaVersion", VERSION_MAX_LENGTH);
        return new AnalysisResult(analysisId, resultJson, modelName, promptVersion, schemaVersion);
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
