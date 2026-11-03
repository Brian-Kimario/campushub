package com.campushub.skills;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Composite key of {@code user_skills}: one row per student and skill. */
@Embeddable
public class UserSkillKey implements Serializable {

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID skillId;

    protected UserSkillKey() {
    }

    public UserSkillKey(UUID userId, UUID skillId) {
        this.userId = userId;
        this.skillId = skillId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserSkillKey key
                && Objects.equals(userId, key.userId)
                && Objects.equals(skillId, key.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, skillId);
    }
}
