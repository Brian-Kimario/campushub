package com.campushub.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.identity.Role;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Token validation without a database (TEST_STRATEGY §4.4): every rejected token gets UNAUTHENTICATED. */
class TokenValidationTest {

    private static final String SECRET = "unit-test-secret-of-more-than-32-bytes-long";
    private static final String ISSUER = "campushub";
    private static final String AUDIENCE = "campushub-api";
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtTokenService service = new JwtTokenService(
            new JwtProperties(SECRET, ISSUER, AUDIENCE, 15), clock);

    @Test
    void issuedTokenParsesBackToItsSubjectRoleAndId() {
        UUID userId = UUID.randomUUID();
        JwtTokenService.IssuedToken issued = service.issue(userId, Role.FACULTY);

        JwtTokenService.ParsedToken parsed = service.parse(issued.value());

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.role()).isEqualTo(Role.FACULTY);
        assertThat(parsed.jti()).isEqualTo(issued.jti());
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void malformedTokensAreRejected() {
        assertUnauthenticated("not-a-jwt");
        assertUnauthenticated("a.b.c");
        assertUnauthenticated("");
    }

    @Test
    void unsecuredAlgNoneTokenIsRejected() {
        String token = new PlainJWT(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600))).serialize();

        assertUnauthenticated(token);
    }

    @Test
    void tokenSignedWithAnotherAlgorithmIsRejected() throws JOSEException {
        String longSecret = "hs512-" + "x".repeat(70); // HS512 needs a key of at least 64 bytes
        String token = sign(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)),
                JWSAlgorithm.HS512, longSecret);

        assertUnauthenticated(token);
    }

    @Test
    void tokenWithWrongSignatureIsRejected() throws JOSEException {
        String token = sign(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)),
                JWSAlgorithm.HS256, "another-secret-of-more-than-32-bytes-long!!");

        assertUnauthenticated(token);
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)))
                .issuer("someone-else").build();

        assertUnauthenticated(sign(claims, JWSAlgorithm.HS256, SECRET));
    }

    @Test
    void tokenWithWrongAudienceIsRejected() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)))
                .audience("other-api").build();

        assertUnauthenticated(sign(claims, JWSAlgorithm.HS256, SECRET));
    }

    @Test
    void expiredTokenIsRejected() throws JOSEException {
        String token = sign(claims(UUID.randomUUID(), Role.STUDENT, NOW.minusSeconds(1)),
                JWSAlgorithm.HS256, SECRET);

        assertUnauthenticated(token);
    }

    @Test
    void tokenWithoutTokenIdIsRejected() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)))
                .jwtID(null).build();

        assertUnauthenticated(sign(claims, JWSAlgorithm.HS256, SECRET));
    }

    @Test
    void tokenWithUnknownRoleIsRejected() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(claims(UUID.randomUUID(), Role.STUDENT, NOW.plusSeconds(600)))
                .claim("role", "SUPERUSER").build();

        assertUnauthenticated(sign(claims, JWSAlgorithm.HS256, SECRET));
    }

    private JWTClaimsSet claims(UUID subject, Role role, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", role.name())
                .build();
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, String secret) throws JOSEException {
        JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private void assertUnauthenticated(String token) {
        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
