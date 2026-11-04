package com.campushub.skills;

import java.util.UUID;

/**
 * One element of the {@code PUT /users/me/skills} body. The evidence note is optional; a blank one
 * is stored as null. Field rules are checked in {@link SkillService}, which also reports the index
 * of the element that failed.
 */
public record UserSkillEntry(UUID skillId, SkillProficiency proficiency, String evidenceNote) {

    public UserSkillEntry {
        evidenceNote = evidenceNote == null || evidenceNote.isBlank() ? null : evidenceNote.strip();
    }
}
