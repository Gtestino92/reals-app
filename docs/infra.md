# Frontend Infrastructure

## What This Repo Builds

This repository is the Android frontend. It does not run as a long-lived Docker service in production. The deployable artifact is an APK/AAB produced by Gradle and later distributed through an Android channel.

The Docker image in this repo is a reproducible build environment. It builds the local debug APK and stores it inside the image at:

```text
/workspace/app/build/outputs/apk/local/debug/
```

## Build Variants

The app has one flavor dimension, `environment`:

| Flavor | Default API URL | Cleartext HTTP |
| --- | --- | --- |
| `local` | `http://10.0.2.2:8080/` | yes |
| `dev` | `https://api-dev.reals.example.com/` | no |
| `prod` | `https://api.reals.example.com/` | no |

The URL is compiled into `BuildConfig.REALS_BASE_URL`. Override defaults with Gradle properties or environment variables:

| Flavor | Gradle property | Environment variable |
| --- | --- | --- |
| `local` | `realsLocalBaseUrl` | `REALS_LOCAL_BASE_URL` |
| `dev` | `realsDevBaseUrl` | `REALS_DEV_BASE_URL` |
| `prod` | `realsProdBaseUrl` | `REALS_PROD_BASE_URL` |

Example:

```bash
./gradlew :app:assembleDevDebug -PrealsDevBaseUrl=https://api-dev.example.com/
```

## Local Build

```bash
./gradlew :app:compileLocalDebugKotlin --no-daemon --console=plain
./gradlew :app:assembleLocalDebug --no-daemon --console=plain
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:compileLocalDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleLocalDebug --no-daemon --console=plain
```

## Docker Build

```bash
docker build -t reals-app:local .
```

To extract the APK:

```bash
id="$(docker create reals-app:local)"
mkdir -p docker-artifacts
docker cp "$id:/workspace/app/build/outputs/apk/local/debug/." docker-artifacts/
docker rm "$id"
```

The compose file is only a convenience wrapper for the build image:

```bash
docker compose --profile build build app-build
```

## GitHub Actions

`.github/workflows/ci.yml` runs on pull requests and pushes to `development`, `main`, and `master`.

It intentionally does not run tests yet. Current checks are:

- Kotlin local debug compilation.
- Local debug APK assembly.
- Docker image build validation.
- APK artifact upload.
- Dependency review on pull requests.

## Versioning

Gradle sets:

- `versionCode` from `realsVersionCode`, `REALS_VERSION_CODE`, then `GITHUB_RUN_NUMBER`, falling back to `1`.
- `versionName` from `realsVersionName`, `REALS_VERSION_NAME`, then `0.1.0-<short-sha>` in CI, falling back to `0.1.0-local`.

Examples:

```bash
./gradlew :app:assembleDevDebug -PrealsVersionCode=42 -PrealsVersionName=0.2.0-dev.42
```

## Secrets

Do not commit Firebase or signing secrets:

- `app/google-services.json`
- `google-services.json`
- `*.jks`
- `*.keystore`
- `secrets/`

The Gradle config applies `com.google.gms.google-services` only when one of these files exists, so CI can compile without Firebase files:

- `app/google-services.json`
- `app/src/local/google-services.json`
- `app/src/dev/google-services.json`
- `app/src/prod/google-services.json`

Use flavor-specific Firebase files when dev/prod Firebase projects diverge.

## Release Signing

Release signing is optional unless all required secrets are present. Provide either:

- `realsReleaseKeystorePath` Gradle property pointing to a local keystore file.
- `REALS_RELEASE_KEYSTORE_BASE64` containing the base64-encoded keystore.

And always provide:

- `REALS_RELEASE_STORE_PASSWORD`
- `REALS_RELEASE_KEY_ALIAS`
- `REALS_RELEASE_KEY_PASSWORD`

Example CI command once secrets exist:

```bash
./gradlew :app:assembleProdRelease --no-daemon --console=plain
```

## Frontend And Backend During Development

The frontend and backend should stay as separate GitHub repositories and separate deployables.

Recommended local setup:

1. Run `reals-backend` with its Docker Compose stack. That starts backend dependencies such as Postgres and MinIO, and exposes the API on `localhost:8080`.
2. Run the Android app from Android Studio or with Gradle install tasks.
3. The Android emulator reaches the host backend through `http://10.0.2.2:8080/`, which is the default `local` flavor `REALS_BASE_URL`.
4. A physical device needs either a LAN-accessible backend URL or a tunneled URL, not `10.0.2.2`.

Recommended remote environments:

- Backend deploys independently as a containerized API service.
- Android builds independently as APK/AAB artifacts.
- The frontend points each build variant/flavor to the correct backend base URL.
- Contract changes are coordinated through `docs/commons/openapi.yaml` and `docs/commons/api.md`; the frontend should update after backend contract changes land.

Do not put both repos into one production Docker Compose as the primary deployment model. Compose is useful for local backend dependencies and build reproducibility, but the Android app is not a server process.
