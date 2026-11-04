package com.campushub.skills;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /admin/skills}. Whitespace is collapsed before the bean rules run. */
public record CreateSkillRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @NotBlank @Size(max = 60) String category) {

    public CreateSkillRequest {
        name = SkillText.collapse(name);
        category = SkillText.collapse(category);
    }
}
