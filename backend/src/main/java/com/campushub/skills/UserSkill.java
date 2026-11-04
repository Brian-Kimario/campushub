package com.campushub.skills;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** A student's skill with its proficiency and optional evidence note (DATA_MODEL §8). */
@Entity
@Table(name = "user_skills")
public class UserSkill {

    @EmbeddedId
    private UserSkillKey key;

    @Enumerated(EnumType.STRING)
    private SkillProficiency proficiency;

    private String evidenceNote;

    private Instant updatedAt;

    protected UserSkill() {
    }

    public UserSkill(UserSkillKey key, SkillProficiency proficiency, String evidenceNote, Instant now) {
        this.key = key;
        this.proficiency = proficiency;
        this.evidenceNote = evidenceNote;
        this.updatedAt = now;
    }

    public UserSkillKey getKey() {
        return key;
    }

    public SkillProficiency getProficiency() {
        return proficiency;
    }

    public String getEvidenceNote() {
        return evidenceNote;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
