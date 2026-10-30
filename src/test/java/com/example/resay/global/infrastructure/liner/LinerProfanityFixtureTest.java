package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LinerProfanityFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsTenMinuteFriendConversationWithExpectedProfanityCounts() throws Exception {
        AnalysisSource source;
        try (InputStream inputStream = Objects.requireNonNull(
                getClass().getResourceAsStream(
                        "/analysis/manual/friend-daily-profanity.json"
                )
        )) {
            source = objectMapper.readValue(inputStream, AnalysisSource.class);
        }

        assertThat(source.durationMs()).isEqualTo(600_000L);
        assertThat(source.segments()).hasSize(50);
        assertThat(count(source, SpeakerRole.SELF, "존나")).isEqualTo(5);
        assertThat(count(source, SpeakerRole.SELF, "시발")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.SELF, "씨발")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.SELF, "미친")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.FRIEND, "존나")).isEqualTo(4);
        assertThat(count(source, SpeakerRole.FRIEND, "시발")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.FRIEND, "씨발")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.FRIEND, "시바")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.FRIEND, "씨바")).isEqualTo(1);
        assertThat(count(source, SpeakerRole.FRIEND, "미친")).isEqualTo(1);
    }

    private long count(
            AnalysisSource source,
            SpeakerRole speakerRole,
            String expression
    ) {
        return source.segments().stream()
                .filter(segment -> segment.speakerRole() == speakerRole)
                .mapToLong(segment -> countOccurrences(segment.content(), expression))
                .sum();
    }

    private int countOccurrences(String content, String expression) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(expression, index)) >= 0) {
            count++;
            index += expression.length();
        }
        return count;
    }
}
