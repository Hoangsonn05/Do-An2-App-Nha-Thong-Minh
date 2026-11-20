# SmartHomeAI — Local RTDB Security Rules (`cloud-rules/`)

Local build and Firebase RTDB Emulator verification suite for **P1.1-B1**.
- Target emulator project: `demo-smarthomeai` (local-only, never touches production `appaintm`).
- No production deploy scripts or `.firebaserc` are included here.

## Prerequisites (Windows)
- Node.js v20+
- Android Studio JBR 21 at `D:\Program Files\Android Studio\jbr` (or override via `FIREBASE_JAVA_HOME` / `ANDROID_STUDIO_JBR` environment variable)

## Commands (PowerShell / Windows)
```powershell
cd cloud-rules
npm install
npm run build:rules
npm run test:build
npm run test:rules
```

## Notes
- `database.rules.src.json` is pure JSON (no JSONC comments).
- `build-rules.mjs` expands rule tokens (`@MEMBER`, `@DEVICE`, `@OWNER`, `@ACTIVE`, `@AUTH`) using callback replacers so `$homeId` / `$deviceId` are preserved verbatim, verifies zero remaining `@TOKEN`s, and writes `database.rules.json`.
- `scripts/run-emulator.mjs` configures `JAVA_HOME` and `PATH` for the child process without modifying Android Studio or system Java settings.
