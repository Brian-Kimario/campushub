package com.campushub.skills;

import java.time.Instant;
import java.util.UUID;

/** One row of a student's skill list, joined to its catalog entry. Built by a JPQL constructor query. */
public record OwnSkillRow(
        UUID skillId,
        String name,
        String category,
        SkillProficiency proficiency,
        String evidenceNote,
        Instant updatedAt) {
}
