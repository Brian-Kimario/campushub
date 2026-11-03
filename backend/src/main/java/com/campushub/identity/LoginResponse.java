package com.campushub.identity;

/** Body of a successful POST /auth/login (API_CONTRACT §2). */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
