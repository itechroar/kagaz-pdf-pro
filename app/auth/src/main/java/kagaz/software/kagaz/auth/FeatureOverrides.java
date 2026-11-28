package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import kagaz.software.SPDF.config.EndpointConfiguration;
import kagaz.software.common.configuration.InstallationPathConfig;

/**
 * Administrator choices about which tools and groups are switched off, kept in a plain file so they
 * survive restarts. One line per entry: {@code endpoint:<name>} or {@code group:<name>}. Only
 * switch-offs are stored; removing a line is how a tool returns to its default.
 *
 * <p>Thread-safe: every public method synchronizes on this instance, and the file is replaced
 * atomically so a crash never leaves it half-written.
 */
@Component
public class FeatureOverrides implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeatureOverrides.class);

    private final EndpointConfiguration endpoints;
    private final Path file;
    private final Set<String> off = new TreeSet<>();

    public FeatureOverrides(
            EndpointConfiguration endpoints,
            @Value("${kagaz.features.file:}") String configured) {
        this.endpoints = endpoints;
        this.file =
                configured.isBlank()
                        ? Path.of(InstallationPathConfig.getConfigPath(), "kagaz-features.txt")
                        : Path.of(configured);
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        try {
            if (Files.exists(file)) {
                off.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
        }
        off.removeIf(String::isBlank);
        for (String entry : off) {
            apply(entry);
        }
        if (!off.isEmpty()) {
            log.info("Applied {} administrator tool switch(es) from {}", off.size(), file);
        }
    }

    private void apply(String entry) {
        if (entry.startsWith("endpoint:")) {
            endpoints.disableEndpoint(entry.substring(9));
        } else if (entry.startsWith("group:")) {
            endpoints.disableGroup(entry.substring(6));
        }
    }

    public synchronized boolean isOff(String kind, String name) {
        return off.contains(kind + ":" + name);
    }

    public synchronized void setOff(String kind, String name, boolean disabled)
            throws IOException {
        String entry = kind + ":" + name;
        if (disabled) {
            off.add(entry);
            apply(entry);
        } else {
            off.remove(entry);
            if ("group".equals(kind)) {
                endpoints.enableGroup(name);
            } else {
                endpoints.enableEndpoint(name);
            }
        }
        persist();
    }

    /** Drops every override and re-enables what they had switched off. */
    public synchronized void resetAll() throws IOException {
        for (String entry : new TreeSet<>(off)) {
            if (entry.startsWith("group:")) {
                endpoints.enableGroup(entry.substring(6));
            } else if (entry.startsWith("endpoint:")) {
                endpoints.enableEndpoint(entry.substring(9));
            }
        }
        off.clear();
        persist();
    }

    private void persist() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, off, StandardCharsets.UTF_8);
        try {
            Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
