package com.campushub.skills;

import java.util.UUID;

/** A catalog entry as the API returns it. */
public record SkillResponse(UUID id, String name, String category) {

    static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory());
    }
}
