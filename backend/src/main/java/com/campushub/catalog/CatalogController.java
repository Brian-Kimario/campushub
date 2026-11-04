package com.campushub.catalog;

import com.campushub.catalog.CatalogDtos.CourseResponse;
import com.campushub.catalog.CatalogDtos.CreateCourseRequest;
import com.campushub.catalog.CatalogDtos.CreateDepartmentRequest;
import com.campushub.catalog.CatalogDtos.CreateTermRequest;
import com.campushub.catalog.CatalogDtos.DepartmentResponse;
import com.campushub.catalog.CatalogDtos.TermResponse;
import com.campushub.catalog.CatalogDtos.UpdateCourseRequest;
import com.campushub.catalog.CatalogDtos.UpdateDepartmentRequest;
import com.campushub.catalog.CatalogDtos.UpdateTermRequest;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catalog endpoints (API_CONTRACT §5). Role rules are enforced in {@link CatalogService}. */
@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog/departments")
    public PageResponse<DepartmentResponse> departments(@RequestParam(required = false) Boolean active,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return catalog.listDepartments(active, sort, page, size);
    }

    @GetMapping("/catalog/terms")
    public PageResponse<TermResponse> terms(@RequestParam(required = false) TermStatus status,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return catalog.listTerms(status, sort, page, size);
    }

    @GetMapping("/catalog/courses")
    public PageResponse<CourseResponse> courses(@RequestParam(required = false) UUID departmentId,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return catalog.listCourses(departmentId, active, sort, page, size);
    }

    @PostMapping("/admin/catalog/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse createDepartment(@AuthenticationPrincipal AuthenticatedUser caller,
                                               @Valid @RequestBody CreateDepartmentRequest body,
                                               HttpServletRequest request) {
        return catalog.createDepartment(caller, body, request);
    }

    @PatchMapping("/admin/catalog/departments/{id}")
    public DepartmentResponse updateDepartment(@AuthenticationPrincipal AuthenticatedUser caller,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody UpdateDepartmentRequest body,
                                               HttpServletRequest request) {
        return catalog.updateDepartment(caller, id, body, request);
    }

    @DeleteMapping("/admin/catalog/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDepartment(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                 HttpServletRequest request) {
        catalog.deleteDepartment(caller, id, request);
    }

    @PostMapping("/admin/catalog/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public TermResponse createTerm(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @Valid @RequestBody CreateTermRequest body, HttpServletRequest request) {
        return catalog.createTerm(caller, body, request);
    }

    @PatchMapping("/admin/catalog/terms/{id}")
    public TermResponse updateTerm(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateTermRequest body, HttpServletRequest request) {
        return catalog.updateTerm(caller, id, body, request);
    }

    @DeleteMapping("/admin/catalog/terms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                           HttpServletRequest request) {
        catalog.deleteTerm(caller, id, request);
    }

    @PostMapping("/admin/catalog/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(@AuthenticationPrincipal AuthenticatedUser caller,
                                       @Valid @RequestBody CreateCourseRequest body, HttpServletRequest request) {
        return catalog.createCourse(caller, body, request);
    }

    @PatchMapping("/admin/catalog/courses/{id}")
    public CourseResponse updateCourse(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                       @Valid @RequestBody UpdateCourseRequest body, HttpServletRequest request) {
        return catalog.updateCourse(caller, id, body, request);
    }

    @DeleteMapping("/admin/catalog/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                             HttpServletRequest request) {
        catalog.deleteCourse(caller, id, request);
    }
}
