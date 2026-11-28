package kagaz.software.kagaz.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** {@code /admin} is the short address of the administrator console. */
@Controller
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AdminRedirect {

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin-dashboard.html";
    }
}
