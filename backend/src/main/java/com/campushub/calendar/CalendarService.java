package com.campushub.calendar;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.milestones.Milestone;
import com.campushub.milestones.MilestoneRepository;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectRepository;
import com.campushub.projects.ProjectService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.tasks.Task;
import com.campushub.tasks.TaskRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deadlines from task and project due dates (API_CONTRACT §9). Each caller only sees deadlines of
 * projects they can already see through {@link ProjectService#canSee}, so the calendar never widens
 * access. A milestone's due instant is shown on its UTC calendar day (ADR-013).
 */
@Service
public class CalendarService {

    /** Largest range a single request may ask for: one month plus a margin for the grid. */
    static final long MAX_RANGE_DAYS = 92;

    private static final Comparator<CalendarEntry> ORDER = Comparator
            .comparing(CalendarEntry::date)
            .thenComparing(CalendarEntry::type)
            .thenComparing(CalendarEntry::title, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CalendarEntry::id);

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final MilestoneRepository milestones;

    public CalendarService(ProjectService projectService, ProjectRepository projects, TaskRepository tasks,
                           MilestoneRepository milestones) {
        this.projectService = projectService;
        this.projects = projects;
        this.tasks = tasks;
        this.milestones = milestones;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntry> entries(AuthenticatedUser caller, LocalDate from, LocalDate to) {
        validateRange(from, to);

        List<CalendarEntry> result = new ArrayList<>();
        for (Project project : projects.findDueBetween(from, to)) {
            if (projectService.canSee(caller, project)) {
                result.add(new CalendarEntry(CalendarEntry.EntryType.PROJECT_DUE, project.getDueDate(), project.getId(),
                        project.getTitle(), project.getStatus().name(), project.getId(), project.getTitle()));
            }
        }

        List<Task> dueTasks = tasks.findDueBetween(from, to);
        List<Milestone> dueMilestones = milestones.findDueBetween(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Map<UUID, Project> projectsById = projects.findAllById(Stream.concat(
                        dueTasks.stream().map(Task::getProjectId),
                        dueMilestones.stream().map(Milestone::getProjectId))
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        for (Task task : dueTasks) {
            Project project = projectsById.get(task.getProjectId());
            if (project != null && projectService.canSee(caller, project)) {
                result.add(new CalendarEntry(CalendarEntry.EntryType.TASK_DUE, task.getDueDate(), task.getId(),
                        task.getTitle(), task.getStatus().name(), project.getId(), project.getTitle()));
            }
        }
        for (Milestone milestone : dueMilestones) {
            Project project = projectsById.get(milestone.getProjectId());
            if (project != null && projectService.canSee(caller, project)) {
                result.add(new CalendarEntry(CalendarEntry.EntryType.MILESTONE_DUE,
                        milestone.getDueAt().atOffset(ZoneOffset.UTC).toLocalDate(), milestone.getId(),
                        milestone.getTitle(), milestone.getStatus().name(), project.getId(), project.getTitle()));
            }
        }
        result.sort(ORDER);
        return result;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "to", "reason", "must be on or after from")));
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.RANGE_TOO_LARGE,
                    "The calendar range can be at most " + MAX_RANGE_DAYS + " days.");
        }
    }
}
