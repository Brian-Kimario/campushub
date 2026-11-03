package com.campushub.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Token settings from {@code campushub.jwt.*} (ADR-003). The secret is never logged or returned. */
@ConfigurationProperties(prefix = "campushub.jwt")
public record JwtProperties(String secret, String issuer, String audience, long accessTtlMinutes) {

    public Duration accessTtl() {
        return Duration.ofMinutes(accessTtlMinutes);
    }
}
