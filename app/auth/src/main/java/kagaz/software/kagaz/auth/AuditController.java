package kagaz.software.kagaz.auth;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only read access to the audit trail (path guarded by {@link AuthFilter}). */
@RestController
@RequestMapping("/api/v1/auth/admin")
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AuditController {

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping("/audit")
    public List<AuditLog.Entry> audit(@RequestParam(defaultValue = "200") int limit) {
        return audit.recent(limit);
    }
}
