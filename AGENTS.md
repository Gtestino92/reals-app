# Agent Instructions

This repository is the Android frontend for Reals, a structured dating / connection product. The app is Kotlin + Jetpack Compose and talks to the Reals backend through documented REST contracts. Preserve backend-owned lifecycle and state-machine behavior when making Android changes.

## Stack

- Android app module: `:app`.
- Kotlin + Jetpack Compose.
- Material 3 Compose UI.
- Retrofit with kotlinx.serialization for backend API calls.
- Firebase Auth for sign-in and Firebase Cloud Messaging for push support.
- Coil for image loading.
- Local JVM tests live under `app/src/test/java`.
- Instrumented Android tests live under `app/src/androidTest/java`.

## Local Development

- Main source lives under `app/src/main/java/com/reals/app`.
- Product flavors:
  - `local`: uses `REALS_LOCAL_BASE_URL`, default `http://10.0.2.2:8080/`, cleartext allowed for emulator/backend local development.
  - `dev`: uses `REALS_DEV_BASE_URL`, default placeholder.
  - `prod`: uses `REALS_PROD_BASE_URL`, default placeholder.
- Firebase config is applied only when a `google-services.json` exists in an expected app location.
- Work branches created or renamed by agents should start with `feature/` unless the task explicitly requires another branch type.

Useful verification commands:

```bash
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:assembleLocalDebug
```

Run only the narrowest useful task first when iterating.

## Architecture Rules

- Backend is the source of truth for lifecycle transitions, penalties, locks, matching, moderation and scheduling state.
- Android may show advisory timers/warnings and disable stale local actions, but must not invent or persist backend lifecycle transitions.
- Do not add backend endpoints or change API paths from Android. Keep Retrofit paths aligned with `docs/commons/api.md` and `docs/commons/openapi.yaml`.
- Do not introduce Navigation Compose unless explicitly requested. Navigation is currently root-state driven through `RealsRootViewModel` and feature coordinators.
- Keep `RealsRootViewModel` as orchestration. Feature-specific behavior belongs in coordinators/handlers such as `FirstChatCoordinator`, `SecondChatCoordinator`, `VisualApprovalCoordinator`, `SchedulingCoordinator`, `HomeCoordinator`, and `ChatMessageActionHandler`.
- Keep Compose screens mostly presentational. They can compute local UI state, call callbacks and display feedback; backend calls and navigation decisions belong in ViewModel/coordinators.
- Repositories should stay data/API adapters. Domain mapping belongs in `data/mapper`.
- DTOs should preserve backend nullable/non-null contracts. Add new fields without removing existing fields unless the backend contract is explicitly removed.

Use this flow unless there is a strong reason not to:

```text
Compose Screen -> RealsRootViewModel -> Coordinator/Handler -> UseCase -> Repository -> RealsApi
```

## Domain And Product Invariants

- The product is anonymous-first and state-driven.
- Do not add swipe behavior, popularity ranking, ELO, visible reputation badges, reveal quotas, WebSockets, notifications beyond documented push types, or ML scoring unless explicitly requested.
- Do not expose native Android admin/backoffice UI unless explicitly requested.
- Do not make Android the source of truth for chat, visual review, scheduling or second-chat lifecycle expiration.
- Safety/report actions can create backend blocks or reports, but frontend must present them as backend-owned outcomes.
- Render backend/user text as plain Compose text. Do not add WebView, HTML rendering or Markdown rendering without a documented sanitization strategy.
- Treat photo URLs as renderable and potentially time-limited. Refetch backend photo/profile responses rather than persisting URL assumptions.

## Documentation

Canonical frontend docs live under `docs/`.

- `docs/README.md`: documentation index.
- `docs/architecture.md`: Android structure and ownership.
- `docs/local-development.md`: local setup and build notes.
- `docs/testing.md`: test strategy and commands.
- `docs/security.md`: frontend security notes.
- `docs/technical-debt-frontend-mvp.md`: MVP decisions and deferred frontend work.
- `docs/technical-debt-frontend-prod.md`: production hardening and future frontend work.
- `docs/commons/`: backend-shared API/domain/state/user-flow contract docs copied from the backend project.

When `docs/commons` and Android code disagree, inspect the backend contract before changing behavior.

## Testing And Verification

- Prefer focused JVM tests for mappers, helpers, coordinators, repositories with fake APIs and pure UI rules.
- Use Compose/instrumented tests only when the behavior truly requires Android runtime or rendering.
- High-value coverage areas when touched:
  - DTO/domain mappers for contract changes;
  - API error code mapping;
  - lifecycle warning/expiry helper logic;
  - coordinator routing after stale backend states;
  - push notification payload handling;
  - Home routing and local hidden interactions;
  - profile photo upload/delete/replace state handling.
- If tests cannot be run, state that clearly and describe the manual/code-level verification performed.

## Review Checklist

Look first for:

- Android accidentally owning backend state transitions;
- stale-action paths that do not refresh Home;
- DTO/schema mismatches with `docs/commons/openapi.yaml`;
- missing user-facing copy for backend error codes;
- Compose screens making backend/navigation decisions directly;
- notification taps deep-linking into stale interactions;
- secrets, tokens, private media URLs or user content leaking into logs;
- missing tests around changed contracts or lifecycle behavior.

## When Unsure

Preserve the current explicit state flow. Ask before changing product behavior, authentication model, navigation model, backend API contracts, matching criteria, moderation/reporting UX or local development assumptions.
