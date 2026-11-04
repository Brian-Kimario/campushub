package com.campushub.identity;

import com.campushub.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Own-profile endpoints (API_CONTRACT §4). */
@RestController
@RequestMapping("/users/me")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser caller) {
        return profiles.profile(caller);
    }

    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
                                  @RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        return profiles.update(caller, body, request);
    }
}
