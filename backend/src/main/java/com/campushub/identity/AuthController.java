package com.campushub.identity;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (API_CONTRACT §2). The refresh token is an HttpOnly cookie (ADR-025).
 * The cookie endpoints also require the {@value RefreshSessionService#CLIENT_HEADER} header.
 * A browser cannot send a custom header cross-site without a CORS preflight, which the API does not
 * allow for other origins, so this blocks cross-site requests that ride on the cookie.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final RefreshSessionService refreshSessions;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter,
                          RefreshSessionService refreshSessions) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.refreshSessions = refreshSessions;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        rateLimiter.check(request.getRemoteAddr());
        return sessionResponse(authService.login(body.email(), body.password(), request));
    }

    /** Restores the session after a reload, or replaces an expired access token (ADR-025). */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshSessionService.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = RefreshSessionService.CLIENT_HEADER, required = false) String clientHeader,
            HttpServletRequest request) {
        requireClientHeader(clientHeader);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        }
        return sessionResponse(authService.refresh(refreshToken, request));
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.currentUser(caller.userId());
    }

    /** Works with an expired access token, so sign-out never gets stuck. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @CookieValue(name = RefreshSessionService.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = RefreshSessionService.CLIENT_HEADER, required = false) String clientHeader,
            HttpServletRequest request) {
        requireClientHeader(clientHeader);
        authService.logout(caller, refreshToken, request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshSessions.clearedCookie().toString())
                .build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                               @Valid @RequestBody ChangePasswordRequest body,
                                               HttpServletRequest request) {
        authService.changePassword(caller.userId(), body.currentPassword(), body.newPassword(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshSessions.clearedCookie().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> sessionResponse(AuthService.IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshSessions.cookie(session.refresh()).toString())
                .body(session.body());
    }

    private static void requireClientHeader(String clientHeader) {
        if (!RefreshSessionService.CLIENT_HEADER_VALUE.equals(clientHeader)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The X-Requested-With header is required.");
        }
    }
}
