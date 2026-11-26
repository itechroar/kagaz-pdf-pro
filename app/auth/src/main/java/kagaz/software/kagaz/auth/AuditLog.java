package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import kagaz.software.common.configuration.InstallationPathConfig;

/**
 * Append-only audit trail. One tab-separated line per {@link AuthEvent}: time, action, actor,
 * target, client address, outcome. The file rotates once to {@code .1} at 10 MB.
 */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AuditLog {

    public record Entry(
            String time, String action, String actor, String target, String ip, boolean success) {}

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path file;

    public AuditLog(@Value("${kagaz.auth.audit-file:}") String configured) {
        this.file =
                configured == null || configured.isBlank()
                        ? Path.of(InstallationPathConfig.getConfigPath(), "kagaz-audit.log")
                        : Path.of(configured);
    }

    @EventListener
    public void on(AuthEvent e) {
        append(
                new Entry(
                        Instant.now().toString(),
                        e.action(),
                        e.actor(),
                        e.target(),
                        e.ip(),
                        e.success()));
    }

    synchronized void append(Entry e) {
        String line =
                String.join(
                                "\t",
                                e.time(),
                                clean(e.action()),
                                clean(e.actor()),
                                clean(e.target()),
                                clean(e.ip()),
                                e.success() ? "ok" : "fail")
                        + "\n";
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
                Files.move(
                        file,
                        file.resolveSibling(file.getFileName() + ".1"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.error("Cannot write audit log {}", file, ex);
        }
    }

    /** Newest first, at most {@code limit} entries (capped at 1000). */
    public synchronized List<Entry> recent(int limit) {
        int max = Math.max(1, Math.min(limit, 1000));
        List<Entry> out = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < max; i--) {
                String[] f = lines.get(i).split("\t", -1);
                if (f.length == 6) {
                    out.add(new Entry(f[0], f[1], f[2], f[3], f[4], "ok".equals(f[5])));
                }
            }
        } catch (IOException ex) {
            log.error("Cannot read audit log {}", file, ex);
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " ");
    }
}
