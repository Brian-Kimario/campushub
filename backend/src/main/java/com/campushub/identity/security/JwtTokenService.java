package com.campushub.identity.security;

import com.campushub.identity.Role;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Issues and validates HS256 access tokens (ADR-003). Only HS256 is accepted, so {@code alg: none}
 * and any other algorithm are refused. Validation checks the signature, issuer, audience, expiry,
 * issue time, token ID, subject, and role claim. Revocation and account state are checked by the filter.
 */
@Component
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    public IssuedToken issue(UUID userId, Role role) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.accessTtl());
        String jti = UUID.randomUUID().toString();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jti)
                .claim(ROLE_CLAIM, role.name())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secretBytes()));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Access token signing failed", ex);
        }
        return new IssuedToken(jwt.serialize(), jti, expiresAt);
    }

    /**
     * @throws ApiException UNAUTHENTICATED for any token that fails validation; the message never
     *     says which check failed, so the response does not help an attacker probe the token
     */
    public ParsedToken parse(String token) {
        Instant now = clock.instant();
        try {
            JWT parsed = JWTParser.parse(token);
            if (!(parsed instanceof SignedJWT signed)
                    || !JWSAlgorithm.HS256.equals(signed.getHeader().getAlgorithm())) {
                throw unauthenticated();
            }
            if (!signed.verify(new MACVerifier(secretBytes()))) {
                throw unauthenticated();
            }
            JWTClaimsSet claims = signed.getJWTClaimsSet();
            if (!properties.issuer().equals(claims.getIssuer())) {
                throw unauthenticated();
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(properties.audience())) {
                throw unauthenticated();
            }
            Date expiresAt = claims.getExpirationTime();
            if (expiresAt == null || !now.isBefore(expiresAt.toInstant())) {
                throw unauthenticated();
            }
            Date issuedAt = claims.getIssueTime();
            if (issuedAt == null) {
                throw unauthenticated();
            }
            String jti = claims.getJWTID();
            if (jti == null || jti.isBlank()) {
                throw unauthenticated();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.getStringClaim(ROLE_CLAIM));
            return new ParsedToken(userId, role, jti, issuedAt.toInstant(), expiresAt.toInstant());
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException ex) {
            throw unauthenticated();
        }
    }

    private byte[] secretBytes() {
        return properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    public record IssuedToken(String value, String jti, Instant expiresAt) {
    }

    public record ParsedToken(UUID userId, Role role, String jti, Instant issuedAt, Instant expiresAt) {
    }
}
