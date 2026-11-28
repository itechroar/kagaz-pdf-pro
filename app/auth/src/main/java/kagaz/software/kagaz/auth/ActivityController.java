package kagaz.software.kagaz.auth;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Administrator-only live view of sign-ins, sign-ups and tool use (guarded by {@link AuthFilter}). */
@RestController
@RequestMapping("/api/v1/auth/admin/activity")
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class ActivityController {

    private final ActivityTracker tracker;
    private final ScheduledExecutorService beat =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "activity-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    public ActivityController(ActivityTracker tracker) {
        this.tracker = tracker;
        beat.scheduleAtFixedRate(tracker::heartbeat, 20, 20, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        beat.shutdownNow();
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return tracker.snapshot();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return tracker.subscribe();
    }
}
