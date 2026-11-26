package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserStoreTest {

    @TempDir Path dir;
    Path file;

    @BeforeEach
    void setUp() {
        file = dir.resolve("users.tsv");
    }

    private UserStore store() {
        return new UserStore(file.toString());
    }

    @Test
    void createAuthenticateAndPersist() {
        UserStore s = store();
        assertTrue(s.isEmpty());
        s.create("Alice", "correct horse", UserStore.Role.ADMIN, false);
        assertTrue(s.authenticate("alice", "correct horse").isPresent());
        assertTrue(s.authenticate("ALICE", "wrong password").isEmpty());
        assertTrue(s.authenticate("nobody", "correct horse").isEmpty());

        UserStore reloaded = store();
        assertEquals(UserStore.Role.ADMIN, reloaded.find("alice").orElseThrow().role());
        assertFalse(readFile().contains("correct horse"));
    }

    @Test
    void rejectsBadInput() {
        UserStore s = store();
        assertThrows(IllegalArgumentException.class, () -> s.create("a", "longenough", null, false));
        assertThrows(IllegalArgumentException.class, () -> s.create("bad name", "longenough", null, false));
        assertThrows(IllegalArgumentException.class, () -> s.create("okname", "short", null, false));
        s.create("okname", "longenough", null, false);
        assertThrows(IllegalArgumentException.class, () -> s.create("OKNAME", "longenough", null, false));
    }

    @Test
    void lastAdminCannotBeRemoved() {
        UserStore s = store();
        s.create("root", "longenough", UserStore.Role.ADMIN, false);
        assertThrows(IllegalStateException.class, () -> s.delete("root"));
        assertThrows(IllegalStateException.class, () -> s.update("root", UserStore.Role.USER, null));
        assertThrows(IllegalStateException.class, () -> s.update("root", null, false));
        s.create("second", "longenough", UserStore.Role.ADMIN, false);
        s.delete("root");
        assertTrue(s.find("root").isEmpty());
    }

    @Test
    void disabledUserCannotLogIn() {
        UserStore s = store();
        s.create("root", "longenough", UserStore.Role.ADMIN, false);
        s.create("bob", "longenough", UserStore.Role.USER, false);
        s.update("bob", null, false);
        assertTrue(s.authenticate("bob", "longenough").isEmpty());
    }

    @Test
    void apiKeyLifecycle() {
        UserStore s = store();
        s.create("bob", "longenough", UserStore.Role.USER, false);
        String key = s.issueApiKey("bob");
        assertTrue(key.startsWith("kpdf_"));
        assertEquals("bob", s.findByApiKey(key).orElseThrow().username());
        assertFalse(Files.exists(file) && readFile().contains(key));
        String second = s.issueApiKey("bob");
        assertTrue(s.findByApiKey(key).isEmpty());
        assertTrue(s.findByApiKey(second).isPresent());
        s.revokeApiKey("bob");
        assertTrue(s.findByApiKey(second).isEmpty());
        assertTrue(s.findByApiKey("garbage").isEmpty());
        assertTrue(s.findByApiKey(null).isEmpty());
    }

    private String readFile() {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void passwordChangeClearsMustChangeFlag() {
        UserStore s = store();
        s.create("bob", "longenough", UserStore.Role.USER, true);
        assertTrue(s.find("bob").orElseThrow().mustChangePassword());
        s.setPassword("bob", "anotherlong", false);
        assertFalse(s.find("bob").orElseThrow().mustChangePassword());
        assertTrue(s.authenticate("bob", "anotherlong").isPresent());
    }
}
