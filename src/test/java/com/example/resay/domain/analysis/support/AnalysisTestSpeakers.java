package com.example.resay.domain.analysis.support;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSpeaker;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;

public final class AnalysisTestSpeakers {

    private AnalysisTestSpeakers() {
    }

    public static List<AnalysisSpeaker> speakersFor(AnalysisScenario scenario) {
        return switch (scenario) {
            case FRIEND_DAILY -> List.of(
                    new AnalysisSpeaker(SpeakerRole.SELF, "호준"),
                    new AnalysisSpeaker(SpeakerRole.FRIEND, "호석")
            );
            case COUPLE_DAILY, COUPLE_CONFLICT -> List.of(
                    new AnalysisSpeaker(SpeakerRole.SELF, "지민"),
                    new AnalysisSpeaker(SpeakerRole.PARTNER, "서연")
            );
            case PARENT_CHILD_CONFLICT -> List.of(
                    new AnalysisSpeaker(SpeakerRole.PARENT, "정희"),
                    new AnalysisSpeaker(SpeakerRole.CHILD, "민준")
            );
        };
    }
}
