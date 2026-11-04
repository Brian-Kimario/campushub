package com.campushub.projects;

/** Trims free-text input and turns blank input into null, so length rules apply to the stored value. */
public final class Text {

    private Text() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
