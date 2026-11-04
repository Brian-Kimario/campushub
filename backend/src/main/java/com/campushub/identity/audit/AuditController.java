package com.campushub.identity.audit;

import com.campushub.shared.web.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator audit search (API_CONTRACT §13). SecurityConfig limits /admin/** to ADMIN. */
@RestController
@RequestMapping("/admin/audit-events")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> search(@RequestParam(required = false) UUID actorId,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) String targetType,
                                                   @RequestParam(required = false) String targetId,
                                                   @RequestParam(required = false) Instant from,
                                                   @RequestParam(required = false) Instant to,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return audit.search(actorId, action, targetType, targetId, from, to, sort, page, size);
    }
}
