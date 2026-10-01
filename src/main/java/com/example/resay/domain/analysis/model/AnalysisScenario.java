package com.example.resay.domain.analysis.model;

import java.util.Set;

// 지원하는 네 가지 관계,상황 조합
public enum AnalysisScenario {
    FRIEND_DAILY(Set.of(SpeakerRole.SELF, SpeakerRole.FRIEND)),
    COUPLE_DAILY(Set.of(SpeakerRole.SELF, SpeakerRole.PARTNER)),
    COUPLE_CONFLICT(Set.of(SpeakerRole.SELF, SpeakerRole.PARTNER)),
    PARENT_CHILD_CONFLICT(Set.of(SpeakerRole.PARENT, SpeakerRole.CHILD));

    private final Set<SpeakerRole> requiredRoles;

    AnalysisScenario(Set<SpeakerRole> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }

    public Set<SpeakerRole> requiredRoles() {
        return requiredRoles;
    }
}
