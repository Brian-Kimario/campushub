package com.campushub.projects;

/** Stored states are PENDING, ACCEPTED and DECLINED; EXPIRED is derived from expiresAt. */
public enum InvitationStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED
}
