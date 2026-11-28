package kagaz.software.SPDF.controller.api.form;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import kagaz.software.common.model.ApplicationProperties;

/**
 * Status and master switch for form detection. Detection runs in the server's own heuristic
 * engine, so there is no model to download: install and uninstall are accepted and do nothing.
 */
@RestController
@RequestMapping("/api/v1/form/form-detection-model")
@RequiredArgsConstructor
public class FormDetectionModelController {

    private final ApplicationProperties properties;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.ofEntries(
                Map.entry("status", "ready"),
                Map.entry("progress", 1),
                Map.entry("activeModelId", "builtin"),
                Map.entry("installed", List.of("builtin")),
                Map.entry("writable", false),
                Map.entry("catalog", List.of()),
                Map.entry("enabled", properties.getFormDetection().isEnabled()),
                Map.entry("serverEngineAvailable", true));
    }

    @PostMapping("/install")
    public Map<String, Object> install() {
        return status();
    }

    @DeleteMapping
    public Map<String, Object> uninstall() {
        return status();
    }

    @PostMapping("/config")
    public Map<String, Object> config(@RequestBody Map<String, Object> body) {
        if (body.get("enabled") instanceof Boolean enabled) {
            properties.getFormDetection().setEnabled(enabled);
        }
        return status();
    }
}
