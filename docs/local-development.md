# Local Development

## Prerequisites

- Android Studio or IntelliJ IDEA with Android support.
- JDK compatible with the Gradle Android plugin used by the project.
- Android SDK for compile/target SDK configured in `app/build.gradle.kts`.
- Firebase configuration if testing Firebase Auth or FCM.

## Flavors

The app has one environment flavor dimension:

- `local`: local backend from emulator. Default base URL is `http://10.0.2.2:8080/`; cleartext traffic is allowed.
- `dev`: dev/staging backend. Set `realsDevBaseUrl` or `REALS_DEV_BASE_URL`.
- `prod`: production backend. Set `realsProdBaseUrl` or `REALS_PROD_BASE_URL`.

The local base URL can be overridden with Gradle property `realsLocalBaseUrl` or environment variable `REALS_LOCAL_BASE_URL`.

## Firebase

The Google Services plugin is applied only when one of these files exists:

- `app/google-services.json`
- `app/src/local/google-services.json`
- `app/src/dev/google-services.json`
- `app/src/prod/google-services.json`

Firebase Auth is required for real sign-in/provisioning flows. Push notification testing requires FCM configuration and Android notification permission on Android 13+.

## Useful Commands

```bash
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:assembleLocalDebug
```

Use the `localDebug` variant for emulator testing against a backend running on the host machine.

## Backend Contract Docs

The Android repo includes backend-shared docs under `docs/commons/`. Refresh those docs from the backend project when API/domain contracts change, then update Android DTOs, mappers, error handling and UI behavior as needed.
