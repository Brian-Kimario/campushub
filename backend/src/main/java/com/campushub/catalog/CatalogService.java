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
import com.campushub.identity.Role;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Catalog use cases: departments, academic terms and courses (FR-CAT-01 to FR-CAT-04, API_CONTRACT §5).
 *
 * <p>Reads are open to any signed-in user and cached. Writes are administrator-only. Every write evicts the
 * cache it touches after the transaction commits, so a reader cannot re-cache a value from before the write.
 * A row still referenced by a user, project or course is not deleted (FR-CAT-03).
 */
@Service
public class CatalogService {

    static final String DEPARTMENTS = "departments";
    static final String TERMS = "terms";
    static final String COURSES = "courses";

    private static final String DEPARTMENT = "department";
    private static final String TERM = "academic_term";
    private static final String COURSE = "course";

    private final DepartmentRepository departments;
    private final AcademicTermRepository terms;
    private final CourseRepository courses;
    private final AuditService audit;
    private final CacheManager cacheManager;
    private final Clock clock;

    public CatalogService(DepartmentRepository departments, AcademicTermRepository terms, CourseRepository courses,
                          AuditService audit, CacheManager cacheManager, Clock clock) {
        this.departments = departments;
        this.terms = terms;
        this.courses = courses;
        this.audit = audit;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    @Cacheable(cacheNames = DEPARTMENTS)
    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> listDepartments(Boolean active, String sort, int page, int size) {
        var order = CatalogSort.parse(sort, "name", Set.of("name", "code"));
        Page<DepartmentResponse> result = departments.search(active, Pagination.request(page, size, order))
                .map(DepartmentResponse::from);
        return PageResponse.of(result, CatalogSort.applied(order));
    }

    @Cacheable(cacheNames = TERMS)
    @Transactional(readOnly = true)
    public PageResponse<TermResponse> listTerms(TermStatus status, String sort, int page, int size) {
        var order = CatalogSort.parse(sort, "startsOn", Set.of("startsOn", "name", "code"));
        Page<TermResponse> result = terms.search(status, Pagination.request(page, size, order))
                .map(TermResponse::from);
        return PageResponse.of(result, CatalogSort.applied(order));
    }

    @Cacheable(cacheNames = COURSES)
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listCourses(UUID departmentId, Boolean active, String sort, int page,
                                                    int size) {
        var order = CatalogSort.parse(sort, "code", Set.of("code", "title"));
        Page<CourseResponse> result = courses.search(departmentId, active, Pagination.request(page, size, order))
                .map(CourseResponse::from);
        return PageResponse.of(result, CatalogSort.applied(order));
    }

    @Transactional
    public DepartmentResponse createDepartment(AuthenticatedUser caller, CreateDepartmentRequest body,
                                               HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        if (departments.existsByCode(body.code())) {
            throw duplicate("A department with this code already exists.");
        }
        Department department = new Department(UUID.randomUUID(), body.code(), body.name(), clock.instant());
        try {
            departments.saveAndFlush(department);
        } catch (DataIntegrityViolationException race) {
            throw duplicate("A department with this code already exists.");
        }
        evictOnCommit(DEPARTMENTS);
        audit.record("catalog.department.created", caller.userId(), DEPARTMENT, department.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse updateDepartment(AuthenticatedUser caller, UUID id, UpdateDepartmentRequest body,
                                               HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Department department = departments.findById(id).orElseThrow(CatalogService::notFound);
        department.update(body.name(), body.active(), clock.instant());
        evictOnCommit(DEPARTMENTS);
        audit.record("catalog.department.updated", caller.userId(), DEPARTMENT, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return DepartmentResponse.from(department);
    }

    @Transactional
    public void deleteDepartment(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Department department = departments.findById(id).orElseThrow(CatalogService::notFound);
        if (departments.countUsers(id) + departments.countCourses(id) > 0) {
            throw inUse();
        }
        try {
            departments.delete(department);
            departments.flush();
        } catch (DataIntegrityViolationException race) {
            throw inUse();
        }
        evictOnCommit(DEPARTMENTS);
        audit.record("catalog.department.deleted", caller.userId(), DEPARTMENT, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
    }

    @Transactional
    public TermResponse createTerm(AuthenticatedUser caller, CreateTermRequest body, HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        requireDateOrder(body.startsOn(), body.endsOn());
        if (terms.existsByCode(body.code())) {
            throw duplicate("A term with this code already exists.");
        }
        var status = body.status() == null ? TermStatus.ACTIVE : body.status();
        AcademicTerm term = new AcademicTerm(UUID.randomUUID(), body.code(), body.name(), body.startsOn(),
                body.endsOn(), status, clock.instant());
        try {
            terms.saveAndFlush(term);
        } catch (DataIntegrityViolationException race) {
            throw duplicate("A term with this code already exists.");
        }
        evictOnCommit(TERMS);
        audit.record("catalog.term.created", caller.userId(), TERM, term.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return TermResponse.from(term);
    }

    @Transactional
    public TermResponse updateTerm(AuthenticatedUser caller, UUID id, UpdateTermRequest body,
                                   HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        AcademicTerm term = terms.findById(id).orElseThrow(CatalogService::notFound);
        // Check the merged dates, so a single changed bound is compared with the stored other bound.
        var startsOn = body.startsOn() == null ? term.getStartsOn() : body.startsOn();
        var endsOn = body.endsOn() == null ? term.getEndsOn() : body.endsOn();
        requireDateOrder(startsOn, endsOn);
        term.update(body.name(), body.startsOn(), body.endsOn(), body.status(), clock.instant());
        evictOnCommit(TERMS);
        audit.record("catalog.term.updated", caller.userId(), TERM, id.toString(), AuditOutcome.SUCCESS, request,
                Map.of());
        return TermResponse.from(term);
    }

    @Transactional
    public void deleteTerm(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        AcademicTerm term = terms.findById(id).orElseThrow(CatalogService::notFound);
        if (terms.countProjects(id) > 0) {
            throw inUse();
        }
        try {
            terms.delete(term);
            terms.flush();
        } catch (DataIntegrityViolationException race) {
            throw inUse();
        }
        evictOnCommit(TERMS);
        audit.record("catalog.term.deleted", caller.userId(), TERM, id.toString(), AuditOutcome.SUCCESS, request,
                Map.of());
    }

    @Transactional
    public CourseResponse createCourse(AuthenticatedUser caller, CreateCourseRequest body,
                                       HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        if (!departments.existsById(body.departmentId())) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "departmentId", "reason", "is not in the catalog")));
        }
        if (courses.existsByCode(body.code())) {
            throw duplicate("A course with this code already exists.");
        }
        Course course = new Course(UUID.randomUUID(), body.code(), body.title(), body.departmentId(), clock.instant());
        try {
            courses.saveAndFlush(course);
        } catch (DataIntegrityViolationException race) {
            throw duplicate("A course with this code already exists.");
        }
        evictOnCommit(COURSES);
        audit.record("catalog.course.created", caller.userId(), COURSE, course.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse updateCourse(AuthenticatedUser caller, UUID id, UpdateCourseRequest body,
                                       HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Course course = courses.findById(id).orElseThrow(CatalogService::notFound);
        course.update(body.title(), body.active(), clock.instant());
        evictOnCommit(COURSES);
        audit.record("catalog.course.updated", caller.userId(), COURSE, id.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
        return CourseResponse.from(course);
    }

    @Transactional
    public void deleteCourse(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Course course = courses.findById(id).orElseThrow(CatalogService::notFound);
        if (courses.countProjects(id) > 0) {
            throw inUse();
        }
        try {
            courses.delete(course);
            courses.flush();
        } catch (DataIntegrityViolationException race) {
            throw inUse();
        }
        evictOnCommit(COURSES);
        audit.record("catalog.course.deleted", caller.userId(), COURSE, id.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
    }

    private static void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    private static void requireDateOrder(LocalDate startsOn, LocalDate endsOn) {
        if (!endsOn.isAfter(startsOn)) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "endsOn", "reason", "must be after startsOn")));
        }
    }

    /** Clears the cache once the surrounding transaction commits, never before. */
    private void evictOnCommit(String cacheName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
        });
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Catalog item not found.");
    }

    private static ApiException duplicate(String message) {
        return new ApiException(ErrorCode.DUPLICATE, message);
    }

    private static ApiException inUse() {
        return new ApiException(ErrorCode.CATALOG_ITEM_IN_USE, "This item is referenced and cannot be deleted.");
    }
}
