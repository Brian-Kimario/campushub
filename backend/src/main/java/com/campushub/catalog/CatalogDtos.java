package com.campushub.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response shapes for the catalog endpoints (API_CONTRACT §5). */
final class CatalogDtos {

    private CatalogDtos() {
    }

    record DepartmentResponse(UUID id, String code, String name, boolean active) {
        static DepartmentResponse from(Department department) {
            return new DepartmentResponse(department.getId(), department.getCode(), department.getName(),
                    department.isActive());
        }
    }

    record CreateDepartmentRequest(@NotBlank @Size(max = 20) String code,
                                   @NotBlank @Size(min = 2, max = 150) String name) {
    }

    record UpdateDepartmentRequest(@Size(min = 2, max = 150) String name, Boolean active) {
    }

    record TermResponse(UUID id, String code, String name, LocalDate startsOn, LocalDate endsOn,
                        TermStatus status) {
        static TermResponse from(AcademicTerm term) {
            return new TermResponse(term.getId(), term.getCode(), term.getName(), term.getStartsOn(),
                    term.getEndsOn(), term.getStatus());
        }
    }

    record CreateTermRequest(@NotBlank @Size(max = 20) String code,
                             @NotBlank @Size(max = 100) String name,
                             @NotNull LocalDate startsOn,
                             @NotNull LocalDate endsOn,
                             TermStatus status) {
    }

    record UpdateTermRequest(@Size(min = 1, max = 100) String name,
                             LocalDate startsOn,
                             LocalDate endsOn,
                             TermStatus status) {
    }

    record CourseResponse(UUID id, String code, String title, UUID departmentId, boolean active) {
        static CourseResponse from(Course course) {
            return new CourseResponse(course.getId(), course.getCode(), course.getTitle(),
                    course.getDepartmentId(), course.isActive());
        }
    }

    record CreateCourseRequest(@NotBlank @Size(max = 20) String code,
                               @NotBlank @Size(max = 200) String title,
                               @NotNull UUID departmentId) {
    }

    record UpdateCourseRequest(@Size(min = 1, max = 200) String title, Boolean active) {
    }
}
