package com.campushub.skills;

import java.time.Instant;
import java.util.UUID;

/** One of the caller's own skills, with the catalog name joined in. */
public record OwnSkillResponse(
        UUID skillId,
        String name,
        String category,
        SkillProficiency proficiency,
        String evidenceNote,
        Instant updatedAt) {

    static OwnSkillResponse from(OwnSkillRow row) {
        return new OwnSkillResponse(row.skillId(), row.name(), row.category(), row.proficiency(),
                row.evidenceNote(), row.updatedAt());
    }
}
