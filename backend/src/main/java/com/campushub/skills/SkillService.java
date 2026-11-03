package com.campushub.skills;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill use cases (FR-SKILL-01, FR-SKILL-02, AUTHORIZATION_MATRIX §3.9).
 *
 * <p>The catalog is readable by any signed-in user. Only an administrator adds to it. A student's own
 * skills are readable and writable only by that student; any other role gets 403 FORBIDDEN_ROLE, and
 * administrators and faculty never see another student's skills (ADR-007, OD-5).
 */
@Service
public class SkillService {

    static final int MAX_OWN_SKILLS = 50;
    static final int MAX_EVIDENCE_LENGTH = 500;

    private static final List<String> CATALOG_SORT = List.of("name,asc", "id,asc");
    private static final String SKILL = "skill";
    private static final String USER_SKILLS = "user_skills";

    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final AuditService audit;
    private final Clock clock;

    public SkillService(SkillRepository skills, UserSkillRepository userSkills, AuditService audit, Clock clock) {
        this.skills = skills;
        this.userSkills = userSkills;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillResponse> catalog(String category, int page, int size) {
        String filter = category == null || category.isBlank() ? null : SkillText.collapse(category);
        var pageable = Pagination.request(page, size, Sort.by(Sort.Direction.ASC, "name", "id"));
        Page<SkillResponse> result = skills.search(filter, pageable).map(SkillResponse::from);
        return PageResponse.of(result, CATALOG_SORT);
    }

    @Transactional
    public SkillResponse create(AuthenticatedUser caller, CreateSkillRequest body, HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        String key = SkillText.key(body.name());
        if (skills.existsByNameKey(key)) {
            throw duplicate();
        }
        Skill skill = new Skill(UUID.randomUUID(), body.name(), key, body.category());
        try {
            skills.saveAndFlush(skill);
        } catch (DataIntegrityViolationException race) {
            // A concurrent insert won the unique key between the check and the flush.
            throw duplicate();
        }
        audit.record("skill.created", caller.userId(), SKILL, skill.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return SkillResponse.from(skill);
    }

    @Transactional(readOnly = true)
    public List<OwnSkillResponse> ownSkills(AuthenticatedUser caller) {
        requireRole(caller, Role.STUDENT);
        return userSkills.findOwnSkills(caller.userId()).stream().map(OwnSkillResponse::from).toList();
    }

    /**
     * Replaces the caller's whole skill set in one transaction (API_CONTRACT §11). Every element is
     * checked before anything is written, so a refused request leaves the previous set in place.
     */
    @Transactional
    public List<OwnSkillResponse> replaceOwnSkills(AuthenticatedUser caller, List<UserSkillEntry> body,
                                                   HttpServletRequest request) {
        requireRole(caller, Role.STUDENT);
        if (body == null) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "body", "reason", "must be an array of skills")));
        }
        List<Map<String, String>> errors = new ArrayList<>();
        if (body.size() > MAX_OWN_SKILLS) {
            errors.add(Map.of("field", "body", "reason", "must contain at most " + MAX_OWN_SKILLS + " skills"));
        }
        Set<UUID> activeIds = activeCatalogIds(body);
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < body.size(); index++) {
            checkEntry(body.get(index), index, activeIds, seen, errors);
        }
        if (!errors.isEmpty()) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", errors);
        }

        UUID userId = caller.userId();
        userSkills.deleteForUser(userId);
        var now = clock.instant();
        List<UserSkill> rows = body.stream()
                .map(entry -> new UserSkill(new UserSkillKey(userId, entry.skillId()), entry.proficiency(),
                        entry.evidenceNote(), now))
                .toList();
        userSkills.saveAllAndFlush(rows);
        audit.record("user_skills.replaced", userId, USER_SKILLS, userId.toString(),
                AuditOutcome.SUCCESS, request, Map.of("count", String.valueOf(rows.size())));
        return ownSkills(caller);
    }

    /** Catalog ids of the skills named in the body that exist and are active. Null ids are skipped. */
    private Set<UUID> activeCatalogIds(List<UserSkillEntry> body) {
        List<UUID> ids = body.stream().map(UserSkillEntry::skillId).filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return skills.findAllById(ids).stream()
                .filter(Skill::isActive)
                .map(Skill::getId)
                .collect(Collectors.toSet());
    }

    private static void checkEntry(UserSkillEntry entry, int index, Set<UUID> activeIds, Set<UUID> seen,
                                   List<Map<String, String>> errors) {
        String field = "skills[" + index + "]";
        if (entry.skillId() == null) {
            errors.add(Map.of("field", field + ".skillId", "reason", "is required"));
        } else if (!activeIds.contains(entry.skillId())) {
            errors.add(Map.of("field", field + ".skillId", "reason", "is not in the catalog"));
        } else if (!seen.add(entry.skillId())) {
            errors.add(Map.of("field", field + ".skillId", "reason", "appears more than once"));
        }
        if (entry.proficiency() == null) {
            errors.add(Map.of("field", field + ".proficiency", "reason",
                    "must be one of BEGINNER, INTERMEDIATE, ADVANCED, EXPERT"));
        }
        if (entry.evidenceNote() != null && entry.evidenceNote().length() > MAX_EVIDENCE_LENGTH) {
            errors.add(Map.of("field", field + ".evidenceNote", "reason",
                    "must be " + MAX_EVIDENCE_LENGTH + " characters or fewer"));
        }
    }

    private static void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    private static ApiException duplicate() {
        return new ApiException(ErrorCode.DUPLICATE, "A skill with this name already exists.");
    }
}
