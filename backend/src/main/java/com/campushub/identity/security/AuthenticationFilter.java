package com.campushub.identity.security;

import com.campushub.identity.UserAccount;
import com.campushub.identity.RevokedTokenRepository;
import com.campushub.identity.UserAccountRepository;
import com.campushub.identity.UserStatus;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a bearer token and applies the checks in AUTHORIZATION_MATRIX §1, steps 1 and 2:
 * signature and claims, token not revoked, account ACTIVE, not issued before {@code revoked_after},
 * and the forced-password-change gate. Role checks (step 3) run later, in the authorization rules.
 *
 * <p>Not a Spring bean. {@code SecurityConfig} adds it to the chain only, so Boot does not also
 * register it as a servlet filter.
 */
public class AuthenticationFilter extends OncePerRequestFilter {

    /** Endpoints a user with a pending forced password change may still call (API_CONTRACT §2). */
    private static final Set<String> PASSWORD_CHANGE_EXEMPT = Set.of(
            "/auth/me", "/auth/logout", "/auth/change-password");

    private final JwtTokenService tokens;
    private final UserAccountRepository users;
    private final RevokedTokenRepository revokedTokens;
    private final ProblemResponder responder;

    public AuthenticationFilter(JwtTokenService tokens, UserAccountRepository users,
                                RevokedTokenRepository revokedTokens, ProblemResponder responder) {
        this.tokens = tokens;
        this.users = users;
        this.revokedTokens = revokedTokens;
        this.responder = responder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            AuthenticatedUser caller = authenticate(header.substring("Bearer ".length()).trim());
            if (caller.mustChangePassword() && !PASSWORD_CHANGE_EXEMPT.contains(request.getRequestURI())) {
                responder.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                        "A password change is required before you can continue.");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    caller, null, List.of(new SimpleGrantedAuthority("ROLE_" + caller.role().name()))));
        } catch (ApiException ex) {
            responder.write(request, response, ex.errorCode(), ex.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    private AuthenticatedUser authenticate(String token) {
        JwtTokenService.ParsedToken parsed = tokens.parse(token);
        if (revokedTokens.existsById(parsed.jti())) {
            throw unauthenticated();
        }
        UserAccount account = users.findById(parsed.userId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(AuthenticationFilter::unauthenticated);
        if (account.getRole() != parsed.role()) {
            throw unauthenticated();
        }
        if (isIssuedBeforeRevocation(parsed, account.getRevokedAfter())) {
            throw unauthenticated();
        }
        return new AuthenticatedUser(
                account.getId(), account.getRole(), parsed.jti(), parsed.expiresAt(), account.isMustChangePassword());
    }

    /**
     * Compares whole seconds (ADR-019). A token issued in the same second as the revocation instant
     * is refused, so the check fails closed.
     */
    static boolean isIssuedBeforeRevocation(JwtTokenService.ParsedToken parsed, Instant revokedAfter) {
        return revokedAfter != null
                && parsed.issuedAt().getEpochSecond() <= revokedAfter.getEpochSecond();
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }
}
