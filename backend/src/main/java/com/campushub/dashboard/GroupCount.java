package com.campushub.dashboard;

/** One row of a grouped count over a single enum column, used by the administrator dashboard. */
public record GroupCount<E extends Enum<E>>(E key, long total) {
}
