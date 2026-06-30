# Android Testing

## Test Locations

- JVM unit tests: `app/src/test/java`.
- Instrumented Android tests: `app/src/androidTest/java`.
- Known test gaps: `app/src/test/java/com/reals/app/TestGaps.md`.

## Primary Commands

```bash
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:compileLocalDebugKotlin
```

Use `assembleLocalDebug` when APK/build integration is the thing being verified:

```bash
./gradlew :app:assembleLocalDebug
```

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
