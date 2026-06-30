# Android Architecture

The Reals Android app is a Kotlin + Jetpack Compose frontend backed by the Reals REST API. The backend owns domain state, lifecycle transitions, penalties, locks, matching, moderation and scheduling rules. Android presents state, collects user actions and refreshes from the backend after important transitions.

## Source Layout

- `app/src/main/java/com/reals/app/MainActivity.kt`: Android entry point and notification-open handling.
- `app/src/main/java/com/reals/app/RealsApplication.kt`: application container entry.
- `app/src/main/java/com/reals/app/di`: dependency wiring.
- `app/src/main/java/com/reals/app/data/api`: Retrofit API interfaces and auth token provider.
- `app/src/main/java/com/reals/app/data/dto`: backend DTOs.
- `app/src/main/java/com/reals/app/data/mapper`: DTO-to-domain mappers.
- `app/src/main/java/com/reals/app/data/repository`: authenticated API repositories.
- `app/src/main/java/com/reals/app/domain/model`: domain models used by Android UI/use cases.
- `app/src/main/java/com/reals/app/domain/usecase`: thin use-case wrappers.
- `app/src/main/java/com/reals/app/ui`: Compose screens, UI rules and root orchestration.
- `app/src/main/java/com/reals/app/notifications`: FCM contracts and local notification helpers.
- `app/src/main/java/com/reals/app/core`: network, security and time helpers.

## Flow

Use this dependency direction:

```text
Compose Screen -> RealsRootViewModel -> Coordinator/Handler -> UseCase -> Repository -> RealsApi
```

Compose screens should stay mostly presentational. ViewModel and coordinators own navigation decisions, backend calls and cross-feature orchestration.

## Root State And Navigation

The app does not use Navigation Compose. Navigation is represented by `RealsRootUiState` and rendered in `RealsApp`.

`RealsRootViewModel` should remain an orchestrator. Feature-specific behavior belongs in:

- `HomeCoordinator`
- `FirstChatCoordinator`
- `SecondChatCoordinator`
- `VisualApprovalCoordinator`
- `SchedulingCoordinator`
- `PartnerProfileCoordinator`
- `ProfileEntryCoordinator`
- `ProfileOperationHandler`
- `ChatMessageActionHandler`

## Contract Changes

When backend contracts change:

1. Update DTOs under `data/dto`.
2. Update domain models under `domain/model` only if UI/domain logic needs the field.
3. Update mappers under `data/mapper`.
4. Update API error mapping in `core/network/ApiError.kt` when new backend error codes are introduced.
5. Add focused tests for DTO/domain mapping and user-facing behavior.

Do not change backend API paths from Android. Keep Retrofit declarations aligned with `docs/commons/openapi.yaml`.

## Lifecycle UX

Frontend countdowns and warnings are advisory only. Backend remains authoritative.

Android may:

- show warnings close to backend deadlines;
- disable stale local actions;
- refresh current state;
- return to Home with clear copy;
- hide interactions locally when the backend intentionally does not lazy-transition reads.

Android must not:

- call fake lifecycle-expiration endpoints;
- mutate backend lifecycle state locally;
- infer locks, penalties or moderation outcomes as source of truth.

## Notifications

Notification handling lives in `notifications/` and `MainActivity`.

Default behavior for notification taps is to open the app and refresh or return to Home. Deep-link only when the target entity is revalidated and the behavior is explicitly requested.
