# Kagaz PDF Admin (desktop)

A separate desktop application for administrators. One window with sections:

| Section | What it is |
| --- | --- |
| Dashboard | Live sign-ins, sign-ups, tool runs per user |
| Users | Create, disable, reset passwords, API keys |
| Tools | Switch tools and groups on or off |
| Audit log | Everything that was done, and by whom |
| Developer | API keys and API reference |
| User app | The complete Kagaz PDF app, so an administrator keeps every user feature |

## Two ways to use it

- **Run on this computer** starts a private server bundled in the app (login on, loopback only) and signs in as its administrator. The password is generated once and kept in the app's settings folder; copy it from Server → Copy local administrator password.
- **Connect to a server** manages a Kagaz PDF server elsewhere. The server must run with `security.enableLogin=true`. You sign in with your own administrator account; the app stores no password. Use an `https` address on any network you do not fully control.

## Build

```
npm ci
npm start          # development; needs a staged server in resources/ for the local mode
npm run dist       # installer
```

`src/server.js`, `src/tools.js` and `shims/` are copied from `../desktop` by `npm run sync` so the two apps cannot drift; the copies are git-ignored.
