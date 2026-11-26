package kagaz.software.kagaz.auth;

/**
 * Published through Spring's event bus for every security-relevant action. Listeners (such as the
 * audit log) subscribe without the publisher knowing about them.
 */
public record AuthEvent(String action, String actor, String target, String ip, boolean success) {}
