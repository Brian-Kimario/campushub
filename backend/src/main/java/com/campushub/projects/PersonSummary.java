package com.campushub.projects;

import java.util.UUID;

/** A user reference inside a project: identity and display name only, never contact or credential fields. */
public record PersonSummary(UUID id, String displayName) {
}
