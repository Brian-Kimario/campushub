package com.campushub.calendar;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One deadline on the academic calendar. Dates are calendar days with no time zone, so a deadline
 * shows on the same day for every viewer (no DST or zone shift).
 */
public record CalendarEntry(
        EntryType type,
        LocalDate date,
        UUID id,
        String title,
        String status,
        UUID projectId,
        String projectTitle) {

    public enum EntryType {
        TASK_DUE,
        PROJECT_DUE,
        MILESTONE_DUE
    }
}
