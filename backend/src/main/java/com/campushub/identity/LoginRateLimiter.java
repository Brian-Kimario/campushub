package com.campushub.identity;

import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limit on login attempts per remote address (ADR-020: 20 per minute). The window
 * lives in memory, so each node enforces it on its own (risk R-09). Stale windows are pruned once
 * the map grows past {@link #MAX_TRACKED_ADDRESSES}.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @throws ApiException RATE_LIMITED with Retry-After when the address is over its limit */
    public void check(String remoteAddress) {
        Instant now = clock.instant();
        if (windows.size() > MAX_TRACKED_ADDRESSES) {
            windows.values().removeIf(window -> !now.isBefore(window.end()));
        }
        Window window = windows.compute(remoteAddress, (address, current) ->
                current == null || !now.isBefore(current.end())
                        ? new Window(now.plus(WINDOW), 1)
                        : new Window(current.end(), current.count() + 1));
        if (window.count() > MAX_ATTEMPTS_PER_WINDOW) {
            long retryAfter = Math.max(1L, Duration.between(now, window.end()).toSeconds());
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many sign-in attempts. Try again later.", retryAfter);
        }
    }

    private record Window(Instant end, int count) {
    }
}
