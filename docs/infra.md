# Frontend Infrastructure

## What This Repo Builds

This repository is the Android frontend. It does not run as a long-lived Docker service in production. The deployable artifact is an APK/AAB produced by Gradle and later distributed through an Android channel.

The Docker image in this repo is a reproducible build environment. It builds the local debug APK and stores it inside the image at:

```text
/workspace/app/build/outputs/apk/local/debug/
```

## Build Variants

The app has one flavor dimension, `environment`:

| Flavor | Application ID | App label | API URL | Cleartext HTTP |
| --- | --- | --- | --- | --- |
| `local` | `com.reals.app.local` | `Reals Local` | Defaults to `http://127.0.0.1:8080/`. | Only local hosts allowed. |
| `dev` | `com.reals.app.dev` | `Reals Dev` | Must be configured to a real HTTPS host. | no |
| `prod` | `com.reals.app` | `Reals` | Must be configured to a real HTTPS host. | no |

The Android namespace remains `com.reals.app`; do not rename Kotlin packages to match flavor application IDs.

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

Current checks are:

- Environment-isolation and App Check dependency validation.
- Local debug unit tests and lint.
- Kotlin local debug compilation.
- Local debug APK assembly.
- Conditional dev debug compilation/APK assembly when `GOOGLE_SERVICES_DEV_JSON_BASE64` and `REALS_DEV_BASE_URL` are configured.
- Conditional prod release compilation/APK assembly when `GOOGLE_SERVICES_PROD_JSON_BASE64` and `REALS_PROD_BASE_URL` are configured.
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
- `app/src/local/google-services.json`
- `app/src/dev/google-services.json`
- `app/src/prod/google-services.json`
- `google-services.json`
- `*.jks`
- `*.keystore`
- `secrets/`

Preferred Firebase client config locations:

- `app/src/local/google-services.json`
- `app/src/dev/google-services.json`
- `app/src/prod/google-services.json`

Each file must contain an Android client for the final application ID:

- `local`: `com.reals.app.local`
- `dev`: `com.reals.app.dev`
- `prod`: `com.reals.app`

`app/google-services.json` is legacy production-only compatibility. Move real production config to
`app/src/prod/google-services.json` before enabling full prod validation.

CI injects Firebase config from base64-encoded secrets when present:

- `GOOGLE_SERVICES_LOCAL_JSON_BASE64`
- `GOOGLE_SERVICES_DEV_JSON_BASE64`
- `GOOGLE_SERVICES_PROD_JSON_BASE64`

Dev/prod build validation also requires:

- `REALS_DEV_BASE_URL`
- `REALS_PROD_BASE_URL`

Release signing remains optional for `assembleProdRelease`; when real signing is required, use the existing
`REALS_RELEASE_KEYSTORE_BASE64`, `REALS_RELEASE_STORE_PASSWORD`, `REALS_RELEASE_KEY_ALIAS`, and
`REALS_RELEASE_KEY_PASSWORD` secrets.

## Firebase App Check Operations

App Check provider by flavor:

| Flavor | Provider | Operational setup |
| --- | --- | --- |
| `local` | Debug provider | Register each developer/device debug secret in Firebase Console. Never commit the secret. |
| `dev` | Play Integrity | Register the dev Firebase Android app for App Check and add the dev signing SHA-256. |
| `prod` | Play Integrity | Register the production Firebase Android app for App Check and add the production signing SHA-256. |

Deployment checklist:

1. Add the correct flavor-specific `google-services.json` outside source control.
2. Register the Android app for App Check in the matching Firebase project.
3. Register SHA-256 signing certificates for `dev` and `prod`.
4. Validate Android against the backend while backend App Check mode is `MONITOR`.
5. Distribute the App Check-enabled Android build to the target environment.
6. Switch backend rollout from `DISABLED` to `MONITOR` to `ENFORCED` only after compatible clients are available.
7. Revoke any exposed local debug token and register a replacement.

Compatibility notes:

- Older backends should ignore the additional `X-Firebase-AppCheck` header.
- Do not switch backend enforcement to `ENFORCED` before this Android version is available in the target environment.
- Validate `dev` first while the backend is in `MONITOR`.
- Production enforcement should occur only after the production Firebase Android app is registered and the distributed
  app uses Play Integrity App Check.
- The Play Integrity verdict policy is a Firebase Console setting, not Android source code.

## Release Optimization

R8, code minification, resource shrinking and broad ProGuard hardening remain intentionally disabled for this
environment-isolation phase. Enable and validate them in a separate release-hardening task after isolated variants are
stable.

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
3. Configure ADB reverse with `.\tools\setup-android-local.ps1` from this repository.
4. The Android emulator or physical device reaches the host backend through `http://127.0.0.1:8080/`, which is the default `local` flavor `REALS_BASE_URL` when ADB reverse is active.

Recommended remote environments:

- Backend deploys independently as a containerized API service.
- Android builds independently as APK/AAB artifacts.
- The frontend points each build variant/flavor to the correct backend base URL.
- Contract changes are coordinated through `docs/commons/openapi.yaml` and `docs/commons/api.md`; the frontend should update after backend contract changes land.

Do not put both repos into one production Docker Compose as the primary deployment model. Compose is useful for local backend dependencies and build reproducibility, but the Android app is not a server process.
