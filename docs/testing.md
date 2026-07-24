# Android Testing

## Test Locations

- JVM unit tests: `app/src/test/java`.
- Instrumented Android tests: `app/src/androidTest/java`.
- Known test gaps: `app/src/test/java/com/reals/app/TestGaps.md`.

## Primary Commands

```bash
./gradlew :app:validateEnvironmentIsolation
./gradlew :app:verifyAppCheckDependencyIsolation
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:lintLocalDebug
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:assembleLocalDebug
./gradlew :app:verifyReleaseBuildHardening
./gradlew :app:compileLocalReleaseKotlin
./gradlew :app:lintLocalRelease
./gradlew :app:assembleLocalRelease
./gradlew :app:verifyLocalReleaseArtifacts
```

Run dev/prod validation only after the required Firebase client config and HTTPS backend URL are available:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:assembleDevDebug
./gradlew :app:compileDevReleaseKotlin
./gradlew :app:assembleDevRelease
./gradlew :app:compileProdReleaseKotlin
./gradlew :app:assembleProdRelease
```

`devDebug` requires `app/src/dev/google-services.json` with `com.reals.app.dev`, `REALS_DEV_BASE_URL` or
`realsDevBaseUrl`, and a registered Firebase App Check debug token for the Firebase Android app with package
`com.reals.app.dev`. `devRelease` additionally requires Play Integrity setup and suitable complete release signing
inputs. `prodRelease` requires `app/src/prod/google-services.json` with `com.reals.app`, `REALS_PROD_BASE_URL` or
`realsProdBaseUrl`, and complete production release signing inputs. These builds must not use placeholder, localhost,
loopback, or cleartext backend URLs.

`localDebug` and `localRelease` keep Firebase Auth, Messaging and local Firebase email auto-verification enabled, but
disable App Check completely. Local backend requests should not acquire an App Check token and should omit
`X-Firebase-AppCheck`.

`testLocalReleaseUnitTest` may be run when present for JVM regression coverage, but JVM tests do not prove R8 runtime
compatibility. R8 compatibility requires building the optimized APK and running the manual smoke test described in
`docs/local-development.md`.

## What To Test

Prefer focused JVM tests for:

- DTO to domain mappers;
- backend error code mapping;
- pure time and lifecycle helpers;
- Home routing and local hidden interactions;
- coordinator action results;
- repository behavior with `FakeRealsApi`;
- Kotlin Serialization DTO contracts for normal responses, backend errors, unknown keys, defaults, nullable fields, and
  request encoding;
- notification contract handling.

Use instrumented or Compose tests for behavior that requires Android runtime, UI rendering, permission APIs, Activity lifecycle or real Compose semantics.

## Contract Change Checklist

When backend docs or OpenAPI change:

1. Add/update DTO fields with correct nullability.
2. Map fields into domain models when the app needs them.
3. Add/update tests in `data/mapper`.
4. Add error mapping tests for new backend error codes.
5. Add coordinator/helper tests for lifecycle and navigation behavior.

## Verification Notes

If a Gradle daemon or Kotlin incremental cache fails after concurrent Gradle tasks, stop daemons and rerun sequentially:

```bash
./gradlew --stop
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:testLocalDebugUnitTest
```

For release-like verification, inspect:

- APK outputs under `app/build/outputs/apk/local/release/`.
- R8 mapping under `app/build/outputs/mapping/localRelease/mapping.txt`.
- APK inspection report under `app/build/reports/release/localRelease-apk-inspection.txt`.

Retain the exact `mapping.txt` for each release artifact. Retrace an obfuscated stack trace with:

```bash
retrace app/build/outputs/mapping/localRelease/mapping.txt obfuscated-stacktrace.txt
```
