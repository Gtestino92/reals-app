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

Home entry uses the backend Home response as the source of truth for profile status and operational interactions.
`ACTIVE` profiles keep the normal Home behavior. A `DRAFT` profile with existing Home interactions can still enter or
remain in Home so first chats, visual reviews, scheduling, second chats and exit actions remain reachable; a `DRAFT`
profile with no Home interaction keeps the profile-completion flow. This distinction is based on Home fields such as
`pendingActions`, `nextSteps`, `passiveNotices` and `activeInteractionsSummary`, not raw match or connection states.
`INACTIVE` is not redefined by this draft-specific routing and continues through the existing profile-only handling.

## Visual Review

`VisualApprovalCoordinator` keeps the backend contract shape unchanged for visual-review partner messages.
Android still maps `partnerPersonalMessageSubmitted`, `partnerPersonalMessageRead` and the legacy
`decisionRequiresPartnerPersonalMessageRead` field, but the legacy field is not used as a client-side decision
eligibility rule.

Unread partner messages are derived from `partnerPersonalMessageSubmitted && !partnerPersonalMessageRead`.
When unread, Android highlights the partner-message card with a `Mensaje nuevo` label and keeps `Leer mensaje`
prominent. Reading is encouraged but optional: approval and rejection remain available while a partner message is
unread, and a message-read failure keeps retry available without blocking the visual decision. Active conflicting
requests, missing visual profile data and expired visual reviews still disable approval and rejection.

## Authentication And Session Validity

The presence of a cached Firebase `currentUser` is not proof that the session is still usable. Authenticated
repositories obtain the current ID token, perform the request, and retry once after an HTTP 401 using one forced
Firebase token refresh.

`FirebaseAuthInvalidUserException` and a missing Firebase user map to the terminal
`AuthFailureReason.NOT_SIGNED_IN` condition. `SessionCoordinator` centralizes terminal invalidation by signing out
Firebase locally and moving the root state to `Login`. Bootstrap failures, authenticated mid-session operations,
email-verification operations and password changes converge on that operation.

Generic token acquisition failures remain `TOKEN_UNAVAILABLE`; they are recoverable and do not force logout.
Recoverable backend `ACCOUNT_DELETED` is a separate state and continues to route to `AccountDeletionPending`.

## Firebase App Check

`RealsApplication.onCreate()` initializes or obtains the default Firebase app for every flavor, installs App Check only
when the flavor enables it, constructs `AppContainer`, and then initializes notification channels. If Firebase
configuration is absent, the app preserves the existing missing-Firebase behavior instead of crashing during startup.

Provider selection is compile-time/flavor-specific:

- `localDebug` and `localRelease`: App Check disabled.
- `devDebug`: debug provider.
- `devRelease`: Play Integrity provider.
- `prodDebug` and `prodRelease`: Play Integrity provider.

`RealsApiClient` owns the common OkHttp application interceptor for App Check when a provider is present. Enabled
variants send:

```http
X-Firebase-AppCheck: <token>
```

The token is never sent in query parameters, URLs, cookies or request bodies, and the network logger redacts the header.
Android relies on the Firebase SDK cache and does not persist raw App Check tokens. In `local`, the App Check interceptor
is absent, no App Check token is requested, and requests including provisioning, authenticated endpoints, legal catalog,
profile/photo operations, local Firebase email verification, chat, matchmaking and scheduling omit
`X-Firebase-AppCheck`.

App Check is separate from Firebase Authentication. App Check verifies the app installation/app binary posture;
Firebase Authentication identifies the signed-in user. App Check does not replace authentication, authorization, rate
limiting, TLS, backend validation or abuse monitoring.

When the backend returns `401 INVALID_APP_CHECK_TOKEN`, the interceptor may inspect a bounded copy of the error body,
force-refresh App Check once, and retry the HTTP request once. It does not retry `MISSING_APP_CHECK_TOKEN`,
`APP_CHECK_VERIFICATION_UNAVAILABLE`, ordinary Firebase Auth errors, network errors or a second invalid-token response.
This retry behavior applies only to App Check-enabled variants.

Firebase Auth ID-token refresh remains repository-owned and is limited to backend `401 INVALID_TOKEN`. App Check
failures, account deletion and access-denied responses must not trigger Firebase Auth refresh or local sign-out.

In the `local` flavor only, `SessionCoordinator` runs the local Firebase
email-verification helper after the backend user is known to be provisioned and
active. The shared `LocalFirebaseEmailVerificationCoordinator` performs:

```text
Firebase reload + forced token refresh
-> backend local verification helper when still unverified
-> Firebase reload + forced token refresh
-> require Verified before ready session routing
```

Deleted or pending-deletion backend users do not run this helper. `dev` and
`prod` skip the helper entirely and preserve the normal email-link verification
flow. The helper never changes profile state or PostgreSQL state; upload and
activation remain backend-enforced through the refreshed Firebase token claim.

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

## Profile Trust Signals

Profile photo validation, photo moderation and profile authenticity verification are backend-owned trust signals.
Android reads the profile/photo fields returned by the API and presents activation errors, but it does not infer
semantic person, full-body, moderation or authenticity-verification outcomes locally.

The shared backend contract is execution-profile aware: outside `prod`, provider `none` may keep MVP-compatible
positive shortcut states; in `prod`, provider `none` does not create positive trust facts. In particular,
`POST /api/me/profile/authenticity-verification` can return `409 AUTHENTICITY_VERIFICATION_NOT_CONFIGURED` when real
profile authenticity verification is not configured. Android currently does not expose a native authenticity
verification flow.

Profile-photo upload and replacement can fail with backend
`EMAIL_NOT_VERIFIED`. Android keeps the original photo action error available
for normal presentation and also marks email verification as required so the
existing remediation actions become visible. The failed upload is not retried
automatically.

Uploading, replacing or deleting a profile photo can make a previously `ACTIVE` profile `DRAFT`. Android must not
cancel, hide or locally discard Home interactions because of that status change. Photo mutations remain allowed during
visual review; Android does not snapshot visual-review photos and does not cancel or restart the visual review after a
profile-photo mutation. `DRAFT` disables new matchmaking only through backend-provided Home matchmaking fields such as
`canSearch` and `blockedReason`.

## Chat Safety Reporting

First and second chat reuse the existing `Reportar y cerrar chat` safety-cancellation flow. The dialog requires
non-blank details (up to 1000 characters) and offers `Comportamiento inapropiado`, `Acoso`, `Seguridad de menores`
and `Otro`. `Seguridad de menores` maps to `ChatExitReason.CHILD_SAFETY_CONCERN`; the selected domain reason and
normalized details flow through the root ViewModel and the corresponding chat coordinator to
`POST /api/chats/{chatId}/safety-cancellations`.

A report expresses a concern, not a confirmed violation. Android does not consume the general safety-report or
admin safety-report APIs and does not expose `priorityReview`; review priority and moderation outcomes remain
backend/backoffice responsibilities.

## Notifications

Notification handling lives in `notifications/` and `MainActivity`.

Default behavior for notification taps is to open the app and refresh or return to Home. Deep-link only when the target entity is revalidated and the behavior is explicitly requested.
