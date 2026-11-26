package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import kagaz.software.common.configuration.InstallationPathConfig;

/**
 * File-backed user directory. One tab-separated line per user, rewritten atomically on every
 * change, so the file is the single source of truth and a crash never leaves it half-written.
 *
 * <p>Thread-safe: every public method synchronizes on this instance. Usernames are normalized to
 * lower case. Passwords are stored as BCrypt hashes; API keys are stored only as SHA-256 hashes
 * and the plain key is returned exactly once, when issued.
 */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class UserStore {

    public enum Role {
        ADMIN,
        USER
    }

    public record User(
            String username,
            String passwordHash,
            Role role,
            boolean enabled,
            String apiKeyHash,
            boolean mustChangePassword,
            long createdAt) {}

    private static final Logger log = LoggerFactory.getLogger(UserStore.class);

    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.@-]{3,64}$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt ignores input beyond 72 bytes
    private static final String API_KEY_PREFIX = "kpdf_";

    private final Path file;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, User> users = new TreeMap<>();
    // Valid hash of a random password, compared against when a username does not exist so a
    // missing user costs the same time as a wrong password.
    private final String decoyHash;

    public UserStore(@Value("${kagaz.auth.users-file:}") String configuredFile) {
        this.file =
                configuredFile == null || configuredFile.isBlank()
                        ? Path.of(InstallationPathConfig.getConfigPath(), "kagaz-users.tsv")
                        : Path.of(configuredFile);
        this.decoyHash = encoder.encode(randomToken(16));
        load();
    }

    public synchronized boolean isEmpty() {
        return users.isEmpty();
    }

    public synchronized List<User> list() {
        return new ArrayList<>(users.values());
    }

    public synchronized Optional<User> find(String username) {
        return Optional.ofNullable(users.get(normalize(username)));
    }

    /** Returns the user only if the password matches and the account is enabled. */
    public synchronized Optional<User> authenticate(String username, String password) {
        User user = users.get(normalize(username));
        String candidate = password == null ? "" : password;
        if (user == null) {
            encoder.matches(candidate, decoyHash);
            return Optional.empty();
        }
        boolean ok = encoder.matches(candidate, user.passwordHash());
        return ok && user.enabled() ? Optional.of(user) : Optional.empty();
    }

    public synchronized Optional<User> findByApiKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
            return Optional.empty();
        }
        byte[] presented = sha256(apiKey);
        for (User user : users.values()) {
            if (user.apiKeyHash() != null
                    && user.enabled()
                    && MessageDigest.isEqual(presented, HexFormat.of().parseHex(user.apiKeyHash()))) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    public synchronized User create(
            String username, String password, Role role, boolean mustChangePassword) {
        String name = normalize(username);
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-64 characters: letters, digits, . _ @ -");
        }
        if (users.containsKey(name)) {
            throw new IllegalArgumentException("User already exists");
        }
        validatePassword(password);
        User user =
                new User(
                        name,
                        encoder.encode(password),
                        role == null ? Role.USER : role,
                        true,
                        null,
                        mustChangePassword,
                        System.currentTimeMillis());
        users.put(name, user);
        save();
        return user;
    }

    public synchronized void setPassword(
            String username, String newPassword, boolean mustChangePassword) {
        User user = require(username);
        validatePassword(newPassword);
        users.put(
                user.username(),
                new User(
                        user.username(),
                        encoder.encode(newPassword),
                        user.role(),
                        user.enabled(),
                        user.apiKeyHash(),
                        mustChangePassword,
                        user.createdAt()));
        save();
    }

    /** Null arguments leave the field unchanged. */
    public synchronized void update(String username, Role role, Boolean enabled) {
        User user = require(username);
        Role newRole = role == null ? user.role() : role;
        boolean newEnabled = enabled == null ? user.enabled() : enabled;
        boolean losesAdmin = user.role() == Role.ADMIN && user.enabled();
        if (losesAdmin && (newRole != Role.ADMIN || !newEnabled) && enabledAdmins() <= 1) {
            throw new IllegalStateException("Cannot remove or disable the last administrator");
        }
        users.put(
                user.username(),
                new User(
                        user.username(),
                        user.passwordHash(),
                        newRole,
                        newEnabled,
                        user.apiKeyHash(),
                        user.mustChangePassword(),
                        user.createdAt()));
        save();
    }

    public synchronized void delete(String username) {
        User user = require(username);
        if (user.role() == Role.ADMIN && user.enabled() && enabledAdmins() <= 1) {
            throw new IllegalStateException("Cannot delete the last administrator");
        }
        users.remove(user.username());
        save();
    }

    /** Replaces any existing key. The returned plain key cannot be recovered later. */
    public synchronized String issueApiKey(String username) {
        User user = require(username);
        String key = API_KEY_PREFIX + randomToken(32);
        putApiKeyHash(user, HexFormat.of().formatHex(sha256(key)));
        return key;
    }

    public synchronized void revokeApiKey(String username) {
        putApiKeyHash(require(username), null);
    }

    private void putApiKeyHash(User user, String hash) {
        users.put(
                user.username(),
                new User(
                        user.username(),
                        user.passwordHash(),
                        user.role(),
                        user.enabled(),
                        hash,
                        user.mustChangePassword(),
                        user.createdAt()));
        save();
    }

    private long enabledAdmins() {
        return users.values().stream()
                .filter(u -> u.role() == Role.ADMIN && u.enabled())
                .count();
    }

    private User require(String username) {
        User user = users.get(normalize(username));
        if (user == null) {
            throw new IllegalArgumentException("No such user");
        }
        return user;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void load() {
        users.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Cannot read user file {}; starting with no users", file, e);
            return;
        }
        List<String> bad = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                String[] f = line.split("\t", -1);
                if (f.length != 7) {
                    throw new IllegalArgumentException("wrong field count");
                }
                users.put(
                        f[0],
                        new User(
                                f[0],
                                f[1],
                                Role.valueOf(f[2]),
                                Boolean.parseBoolean(f[3]),
                                f[4].isEmpty() ? null : f[4],
                                Boolean.parseBoolean(f[5]),
                                Long.parseLong(f[6])));
            } catch (RuntimeException e) {
                bad.add(line);
            }
        }
        if (!bad.isEmpty()) {
            // Keep the unreadable lines for the operator instead of failing to start or losing them.
            log.error("Skipped {} unreadable line(s) in {}; copied to {}.corrupt", bad.size(), file, file);
            try {
                Files.write(
                        Path.of(file + ".corrupt"),
                        bad,
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
                restrict(Path.of(file + ".corrupt"));
            } catch (IOException e) {
                log.error("Could not write {}.corrupt", file, e);
            }
        }
    }

    private void save() {
        StringBuilder out = new StringBuilder("# kagaz users: do not edit while the server runs\n");
        for (User u : users.values()) {
            out.append(u.username())
                    .append('\t')
                    .append(u.passwordHash())
                    .append('\t')
                    .append(u.role())
                    .append('\t')
                    .append(u.enabled())
                    .append('\t')
                    .append(u.apiKeyHash() == null ? "" : u.apiKeyHash())
                    .append('\t')
                    .append(u.mustChangePassword())
                    .append('\t')
                    .append(u.createdAt())
                    .append('\n');
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "kagaz-users", ".tmp");
            Files.writeString(tmp, out.toString(), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem (Windows): rely on the directory's ACL
            }
            try {
                Files.move(
                        tmp,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write user file " + file, e);
        }
    }

    private static void restrict(Path p) {
        try {
            Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // non-POSIX filesystem: nothing to restrict
        }
    }
}
