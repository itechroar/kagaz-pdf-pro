package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import kagaz.software.SPDF.config.EndpointConfiguration;
import kagaz.software.common.model.ApplicationProperties;

class FeatureOverridesTest {

    @TempDir Path dir;

    private static EndpointConfiguration config() {
        return new EndpointConfiguration(new ApplicationProperties(), false, null, null);
    }

    @Test
    void switchOffPersistsAndIsReappliedAfterRestart() throws Exception {
        String file = dir.resolve("f.txt").toString();
        EndpointConfiguration first = config();
        FeatureOverrides overrides = new FeatureOverrides(first, file);
        overrides.setOff("endpoint", "form-detection", true);
        assertFalse(first.isEndpointEnabled("form-detection"));

        EndpointConfiguration second = config();
        assertTrue(second.isEndpointEnabled("form-detection"));
        new FeatureOverrides(second, file).run(null);
        assertFalse(second.isEndpointEnabled("form-detection"));
    }

    @Test
    void switchingBackOnRemovesTheOverride() throws Exception {
        EndpointConfiguration config = config();
        FeatureOverrides overrides =
                new FeatureOverrides(config, dir.resolve("f.txt").toString());
        overrides.setOff("endpoint", "accessibility-report", true);
        overrides.setOff("endpoint", "accessibility-report", false);
        assertTrue(config.isEndpointEnabled("accessibility-report"));
        assertEquals("", Files.readString(dir.resolve("f.txt")));
    }

    @Test
    void resetAllRestoresEverything() throws Exception {
        EndpointConfiguration config = config();
        FeatureOverrides overrides =
                new FeatureOverrides(config, dir.resolve("f.txt").toString());
        overrides.setOff("endpoint", "merge-pdfs", true);
        overrides.resetAll();
        assertTrue(config.isEndpointEnabled("merge-pdfs"));
        assertFalse(overrides.isOff("endpoint", "merge-pdfs"));
    }

    @Test
    void controllerRefusesRemoteCallersWhenLoginIsOff() {
        EndpointConfiguration config = config();
        FeatureController controller =
                new FeatureController(
                        config,
                        new FeatureOverrides(config, dir.resolve("f.txt").toString()),
                        false);
        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("203.0.113.9");
        ResponseStatusException e =
                assertThrows(ResponseStatusException.class, () -> controller.list(remote));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());

        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        assertFalse(controller.list(local).tools().isEmpty());
    }

    @Test
    void controllerRejectsUnknownAndMalformedNames() {
        EndpointConfiguration config = config();
        FeatureController controller =
                new FeatureController(
                        config,
                        new FeatureOverrides(config, dir.resolve("f.txt").toString()),
                        true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(
                HttpStatus.NOT_FOUND,
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        controller.set(
                                                request,
                                                new FeatureController.Switch(
                                                        "endpoint", "no-such-tool", false)))
                        .getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        controller.set(
                                                request,
                                                new FeatureController.Switch(
                                                        "endpoint", "../x", false)))
                        .getStatusCode());
    }
}
