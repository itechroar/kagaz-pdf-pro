# Kagaz PDF desktop

Electron window around the Kagaz PDF server. It starts the bundled server on a free loopback port (127.0.0.1 only), waits for it to be ready, shows it, and stops it on exit. Links to other sites open in the system browser.

## Build

1. Build the server jar from the repo root: `./gradlew build -PbuildWithFrontend=true`, then copy `app/core/build/libs/*.jar` (the boot jar, not `-plain`) to `desktop/resources/kagaz-pdf.jar`.
2. Optional: put a JRE 25 in `desktop/resources/jre/` (e.g. made with `jlink`) so users need no Java. Without it the app uses `java` from PATH.
3. `cd desktop && npm install && npm run dist`. Installers appear in `desktop/dist/`.

Dev run: `npm install && npm start`. Tests (no Electron needed): `npm test`.

User data lives in the OS app-data folder under `Kagaz PDF/data`. Installers are unsigned; code signing is yours to set up.
