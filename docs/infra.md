# Frontend Infrastructure

## What This Repo Builds

This repository is the Android frontend. It does not run as a long-lived Docker service in production. The deployable artifact is an APK/AAB produced by Gradle and later distributed through an Android channel.

The Docker image in this repo is a reproducible build environment. It builds the debug APK and stores it inside the image at:

```text
/workspace/app/build/outputs/apk/debug/
```

## Local Build

```bash
./gradlew :app:compileDebugKotlin --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Docker Build

```bash
docker build -t reals-app:local .
```

To extract the APK:

```bash
id="$(docker create reals-app:local)"
mkdir -p docker-artifacts
docker cp "$id:/workspace/app/build/outputs/apk/debug/." docker-artifacts/
docker rm "$id"
```

The compose file is only a convenience wrapper for the build image:

```bash
docker compose --profile build build app-build
```

## GitHub Actions

`.github/workflows/ci.yml` runs on pull requests and pushes to `development`, `main`, and `master`.

It intentionally does not run tests yet. Current checks are:

- Kotlin debug compilation.
- Debug APK assembly.
- Docker image build validation.
- APK artifact upload.
- Dependency review on pull requests.

## Secrets

Do not commit Firebase or signing secrets:

- `app/google-services.json`
- `google-services.json`
- `*.jks`
- `*.keystore`
- `secrets/`

The current Gradle config applies `com.google.gms.google-services` only when `app/google-services.json` exists, so CI can compile without that file.

## Frontend And Backend During Development

The frontend and backend should stay as separate GitHub repositories and separate deployables.

Recommended local setup:

1. Run `reals-backend` with its Docker Compose stack. That starts backend dependencies such as Postgres and MinIO, and exposes the API on `localhost:8080`.
2. Run the Android app from Android Studio or with Gradle install tasks.
3. The Android emulator reaches the host backend through `http://10.0.2.2:8080/`, which is already the debug `REALS_BASE_URL`.
4. A physical device needs either a LAN-accessible backend URL or a tunneled URL, not `10.0.2.2`.

Recommended remote environments:

- Backend deploys independently as a containerized API service.
- Android builds independently as APK/AAB artifacts.
- The frontend points each build variant/flavor to the correct backend base URL.
- Contract changes are coordinated through `docs/openapi.yaml` and `docs/api.md`; the frontend should update after backend contract changes land.

Do not put both repos into one production Docker Compose as the primary deployment model. Compose is useful for local backend dependencies and build reproducibility, but the Android app is not a server process.