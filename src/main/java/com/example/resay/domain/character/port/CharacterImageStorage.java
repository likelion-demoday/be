package com.example.resay.domain.character.port;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.model.GeneratedCharacterImage;

public interface CharacterImageStorage {

    String save(Long analysisId, SpeakerRole speakerRole, GeneratedCharacterImage image);

    void delete(String objectKey);
}
