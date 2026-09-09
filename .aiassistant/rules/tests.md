---
apply: always
---

# Tests

- Prefer focused JVM tests under `app/src/test/java`.
- Use fake APIs and existing test utilities before adding new test infrastructure.
- High-value tests cover DTO/domain mappers, API error mapping, pure lifecycle helpers, Home routing, coordinator action results and push handling.
- Compose rendering tests belong under `app/src/androidTest/java` and should be added only when rendering/runtime behavior is the point of the change.
- Useful commands:

```bash
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:assembleLocalDebug
```

- If tests cannot be run locally, say so explicitly and describe the verification performed.
