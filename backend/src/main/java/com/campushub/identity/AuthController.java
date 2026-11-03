package com.campushub.identity;

import com.campushub.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints (API_CONTRACT §2). */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        rateLimiter.check(request.getRemoteAddr());
        return authService.login(body.email(), body.password(), request);
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.currentUser(caller.userId());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest request) {
        authService.logout(caller, request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
                               @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        authService.changePassword(caller.userId(), body.currentPassword(), body.newPassword(), request);
    }
}
