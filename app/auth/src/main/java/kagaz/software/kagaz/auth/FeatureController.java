package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import kagaz.software.SPDF.config.EndpointConfiguration;
import kagaz.software.SPDF.config.EndpointConfiguration.DisableReason;

/**
 * Administrator control of which tools are available. With login on, {@link AuthFilter} limits
 * {@code /api/v1/auth/admin/**} to administrators. With login off there is no identity to check, so
 * the API answers only requests from the machine itself (fail closed): a desktop install or a
 * developer on localhost, never a network client.
 */
@RestController
@RequestMapping("/api/v1/auth/admin/features")
public class FeatureController {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,80}$");

    private final EndpointConfiguration endpoints;
    private final FeatureOverrides overrides;
    private final boolean loginEnabled;

    public FeatureController(
            EndpointConfiguration endpoints,
            FeatureOverrides overrides,
            @Value("${security.enableLogin:false}") boolean loginEnabled) {
        this.endpoints = endpoints;
        this.overrides = overrides;
        this.loginEnabled = loginEnabled;
    }

    public record Tool(String name, boolean enabled, String reason, boolean switchedOff) {}

    public record Group(
            String name,
            boolean enabled,
            String reason,
            boolean switchedOff,
            boolean dependency,
            int tools) {}

    public record Listing(List<Tool> tools, List<Group> groups, boolean loginEnabled) {}

    public record Switch(String kind, String name, Boolean enabled) {}

    @GetMapping
    public Listing list(HttpServletRequest request) {
        requireLocalWhenOpen(request);
        List<Tool> tools = new ArrayList<>();
        for (String name : new TreeSet<>(endpoints.getAllEndpoints())) {
            var availability = endpoints.getEndpointAvailability(name);
            tools.add(
                    new Tool(
                            name,
                            availability.isEnabled(),
                            availability.getReason() == null
                                    ? null
                                    : availability.getReason().name(),
                            overrides.isOff("endpoint", name)));
        }
        List<Group> groups = new ArrayList<>();
        for (String name : endpoints.getAllGroups()) {
            DisableReason reason = endpoints.getGroupDisableReason(name);
            groups.add(
                    new Group(
                            name,
                            reason == null,
                            reason == null ? null : reason.name(),
                            overrides.isOff("group", name),
                            endpoints.isToolDependencyGroup(name),
                            endpoints.getEndpointsForGroup(name).size()));
        }
        return new Listing(tools, groups, loginEnabled);
    }

    @PutMapping
    public Map<String, Object> set(HttpServletRequest request, @RequestBody Switch change)
            throws IOException {
        requireLocalWhenOpen(request);
        String kind = change.kind();
        String name = change.name();
        if (!("endpoint".equals(kind) || "group".equals(kind))
                || name == null
                || !NAME.matcher(name).matches()
                || change.enabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
        boolean known =
                "group".equals(kind)
                        ? endpoints.getAllGroups().contains(name)
                        : endpoints.getAllEndpoints().contains(name);
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown " + kind);
        }
        if (change.enabled() && blockedByDependency(kind, name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot enable: a program or module this needs is missing on the server");
        }
        overrides.setOff(kind, name, !change.enabled());
        return Map.of("kind", kind, "name", name, "enabled", change.enabled());
    }

    @DeleteMapping
    public Map<String, Object> reset(HttpServletRequest request) throws IOException {
        requireLocalWhenOpen(request);
        overrides.resetAll();
        return Map.of("reset", true);
    }

    private boolean blockedByDependency(String kind, String name) {
        if ("group".equals(kind)) {
            return endpoints.getGroupDisableReason(name) == DisableReason.DEPENDENCY;
        }
        var availability = endpoints.getEndpointAvailability(name);
        return !availability.isEnabled() && availability.getReason() == DisableReason.DEPENDENCY;
    }

    private void requireLocalWhenOpen(HttpServletRequest request) {
        if (loginEnabled) {
            return;
        }
        try {
            if (InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                return;
            }
        } catch (IOException e) {
            // an unparseable address is treated as remote
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Tool management needs login enabled or a local request");
    }
}
