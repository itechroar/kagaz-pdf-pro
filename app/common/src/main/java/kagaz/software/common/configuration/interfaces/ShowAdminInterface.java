package kagaz.software.common.configuration.interfaces;

public interface ShowAdminInterface {
    default boolean getShowUpdateOnlyAdmins() {
        return true;
    }
}
