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
```

Run dev/prod validation only after the required Firebase client config and HTTPS backend URL are available:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:assembleDevDebug
./gradlew :app:compileProdReleaseKotlin
./gradlew :app:assembleProdRelease
```

`devDebug` requires `app/src/dev/google-services.json` with `com.reals.app.dev` and `REALS_DEV_BASE_URL` or
`realsDevBaseUrl`. `prodRelease` requires `app/src/prod/google-services.json` with `com.reals.app` and
`REALS_PROD_BASE_URL` or `realsProdBaseUrl`. These builds must not use placeholder, localhost, loopback, or cleartext
backend URLs.

## What To Test

Prefer focused JVM tests for:

- DTO to domain mappers;
- backend error code mapping;
- pure time and lifecycle helpers;
- Home routing and local hidden interactions;
- coordinator action results;
- repository behavior with `FakeRealsApi`;
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
