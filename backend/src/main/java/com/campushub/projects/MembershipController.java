package com.campushub.projects;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.MembershipResponses.InvitationResponse;
import com.campushub.projects.MembershipResponses.MemberResponse;
import com.campushub.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Team membership and invitation endpoints (API_CONTRACT §6.1). */
@RestController
@RequestMapping
public class MembershipController {

    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/projects/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return memberships.members(caller, id);
    }

    @DeleteMapping("/projects/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                             @PathVariable UUID userId, HttpServletRequest request) {
        memberships.removeMember(caller, id, userId, request);
    }

    @PostMapping("/projects/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                     @Valid @RequestBody InviteRequest body, HttpServletRequest request) {
        return memberships.invite(caller, id, body, request);
    }

    @GetMapping("/projects/{id}/invitations")
    public PageResponse<InvitationResponse> projectInvitations(@AuthenticationPrincipal AuthenticatedUser caller,
                                                               @PathVariable UUID id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return memberships.invitationsForProject(caller, id, page, size);
    }

    @GetMapping("/invitations/mine")
    public PageResponse<InvitationResponse> myInvitations(@AuthenticationPrincipal AuthenticatedUser caller,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return memberships.myInvitations(caller, page, size);
    }

    @PostMapping("/invitations/{id}/accept")
    public InvitationResponse accept(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                     HttpServletRequest request) {
        return memberships.accept(caller, id, request);
    }

    @PostMapping("/invitations/{id}/decline")
    public InvitationResponse decline(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                      HttpServletRequest request) {
        return memberships.decline(caller, id, request);
    }
}
