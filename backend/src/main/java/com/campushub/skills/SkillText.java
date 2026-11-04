package com.campushub.skills;

import java.util.Locale;

/** Normalization shared by the catalog and the student skill set (FR-SKILL-01). */
final class SkillText {

    private SkillText() {
    }

    /** Trims the value and collapses inner whitespace. Null stays null. */
    static String collapse(String value) {
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }

    /** The unique catalog key: the collapsed name, lowercased. */
    static String key(String collapsedName) {
        return collapsedName.toLowerCase(Locale.ROOT);
    }
}
