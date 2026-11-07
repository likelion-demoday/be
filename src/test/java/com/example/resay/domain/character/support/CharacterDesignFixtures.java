package com.example.resay.domain.character.support;

import com.example.resay.domain.character.model.CharacterDesignSpec;
import java.util.List;

public final class CharacterDesignFixtures {

    private CharacterDesignFixtures() {
    }

    public static CharacterDesignSpec validDesign() {
        return new CharacterDesignSpec(
                "질문 수집가",
                "상대의 말에서 단서를 찾아 질문을 이어 감",
                "짧은 맞장구로 대화 흐름을 유지함",
                "대화의 단서를 차곡차곡 모으는 작은 기록가",
                "rounded owl-inspired creature with short ear tufts",
                "1.2:1 continuous oval silhouette covered in dense velvet micro-fuzz",
                new CharacterDesignSpec.Outfit(
                        "cropped field jacket with a pointed collar, patch pockets, and brass snaps",
                        "fine rib-knit mock-neck pullover",
                        List.of("washed cotton twill", "fine wool knit", "brass"),
                        null
                ),
                List.of(new CharacterDesignSpec.Prop(
                        "miniature accordion notebook",
                        "kraft paper pages with a dark leather cover and brass clasp",
                        "held close to the torso with both short arms"
                )),
                new CharacterDesignSpec.Palette(
                        new CharacterDesignSpec.Color("deep teal", "#287B78"),
                        new CharacterDesignSpec.Color("warm ivory", "#F4E7CF"),
                        new CharacterDesignSpec.Color("rust orange", "#C9633B"),
                        new CharacterDesignSpec.Color("mustard yellow", "#D7A62A")
                ),
                "calm standing pose facing 45 degrees toward the viewer's left",
                "One rounded owl-inspired creature with a dense #287B78 velvet micro-fuzz body, "
                        + "a #F4E7CF wool mock-neck under a #C9633B cotton field jacket, "
                        + "holding a leather notebook with a #D7A62A brass accent, full body, "
                        + "three-quarter view, soft studio lighting, transparent background."
        );
    }
}
