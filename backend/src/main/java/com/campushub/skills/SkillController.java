package com.campushub.skills;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Skill endpoints (API_CONTRACT §11). Role rules are enforced in {@link SkillService}. */
@RestController
public class SkillController {

    private final SkillService skills;

    public SkillController(SkillService skills) {
        this.skills = skills;
    }

    @GetMapping("/skills")
    public PageResponse<SkillResponse> catalog(@RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return skills.catalog(category, page, size);
    }

    @PostMapping("/admin/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                @Valid @RequestBody CreateSkillRequest body, HttpServletRequest request) {
        return skills.create(caller, body, request);
    }

    @GetMapping("/users/me/skills")
    public List<OwnSkillResponse> own(@AuthenticationPrincipal AuthenticatedUser caller) {
        return skills.ownSkills(caller);
    }

    @PutMapping("/users/me/skills")
    public List<OwnSkillResponse> replace(@AuthenticationPrincipal AuthenticatedUser caller,
                                          @RequestBody(required = false) List<UserSkillEntry> body,
                                          HttpServletRequest request) {
        return skills.replaceOwnSkills(caller, body, request);
    }
}
