package com.campushub.identity;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directory lookups for administrators. The admin-only rule for {@code /users/faculty} lives in
 * {@code SecurityConfig}, so the role check runs before this method is reached.
 */
@RestController
@RequestMapping("/users")
public class UserDirectoryController {

    private final UserAccountRepository users;

    public UserDirectoryController(UserAccountRepository users) {
        this.users = users;
    }

    /** Active faculty accounts, for choosing a project supervisor. Not paginated in the MVP (IMPLEMENTATION_LOG §11). */
    @GetMapping("/faculty")
    public List<UserSummary> faculty() {
        return users.findByRoleAndStatusOrderByDisplayNameAsc(Role.FACULTY, UserStatus.ACTIVE).stream()
                .map(UserSummary::from)
                .toList();
    }
}
