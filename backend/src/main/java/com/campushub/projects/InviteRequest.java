package com.campushub.projects;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitations are addressed by the invitee's email, because no student directory exists yet. */
public record InviteRequest(
        @NotBlank @Email @Size(max = 254) String inviteeEmail) {
}
