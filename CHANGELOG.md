# Changelog

## 1.1.0

Everyone signs in, and users and administrators now have separate sites and apps.

**Added**
- Separate administrator site (default port 8081) next to the user site (8080). The user site hides every administrator address; the administrator site serves administrators only. Both share accounts and data.
- Administrator console: live dashboard of sign-ins, sign-ups and tool use per user, users, tool and group switches, audit log, and the full user app.
- Optional self-service sign-up (`KAGAZ_AUTH_SIGNUP_ENABLED`, on in Docker). Sign-up always creates plain users.
- Kagaz PDF Admin: separate desktop installer for Windows, macOS and Linux, and a separate Android app.
- `setup-admin.sh` / `setup-admin.bat` for the administrator site.
- Desktop app can connect to a hosted server so one account works on every device.
- Check PDF accessibility and Auto form detection now work (they were disabled).

**Changed**
- The desktop app now requires sign-in. Docker publishes the administrator site on this computer only by default.
- Administrators who sign in on the administrator site land on the dashboard.

**Upgrading**
- Docker: administrator pages moved from port 8080 to 8081. Set `KAGAZ_ADMIN_HOST_BIND=0.0.0.0` to reach them from other devices (behind a firewall or VPN), or leave `KAGAZ_ADMIN_PORT` unset outside Docker to keep a single site.

## 1.0.3

Initial public release of the Apache-2.0 edition.
