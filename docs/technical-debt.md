# Frontend Technical Debt And Product Decisions

This file lists known pending or intentionally unimplemented frontend behavior for the Android app. Do not implement these implicitly while working on unrelated tasks.

## Product Decisions

* Keep email/password Firebase authentication as the MVP authentication path.
* Defer Google Sign-In and other social providers until after MVP unless onboarding friction becomes a blocker.
* Keep multipart profile-photo upload as the official frontend flow.
* Do not add temporary `isPersonPhoto` / `isFullBody` flags to the Android multipart upload flow. For pre-MVP, let the backend use permissive validation defaults.
* Keep Home polling and chat/scheduling polling as the pre-notification fallback behavior.
* When push notifications are implemented, Home refresh remains the source of truth after notification taps.
* Do not build a native Android admin/backoffice UI for MVP. Safety-report review can continue through backend/admin endpoints and Bruno or another external admin tool.
* Keep manual/dev-only helpers available in local/dev builds only. Production UI should avoid exposing testing shortcuts.

## Pre-MVP Frontend Tasks

### 1. Complete RootViewModel refactor

Status: in progress / assumed before continuing.

Goal:

* Keep `RealsRootViewModel` as orchestration only.
* Move feature-specific logic into coordinators/handlers.
* Preserve current behavior during the refactor.

Expected structure:

* Session/account handling.
* Profile handling.
* Home/matchmaking handling.
* First chat handling.
* Visual approval handling.
* Scheduling handling.
* Second chat handling.

Acceptance criteria:

* No behavior regression in login/provision/profile/home/chat/scheduling flows.
* Home auto-routing still works after queue/match creation.
* Silent polling still avoids overwriting visible UI errors.
* Local hidden Home interactions are still pruned when no longer relevant.

### 2. Hide manual location fallback outside local/dev

The Home screen currently exposes a manual location fallback intended for development/testing.

Pre-MVP requirement:

* Hide the manual latitude/longitude fallback in production builds.
* Keep it available only in local/debug/dev builds or behind a feature flag.
* The user-facing production flow should use device location permission and current device location.

Acceptance criteria:

* Production UI does not show manual coordinate entry.
* Local/dev builds can still manually enter coordinates for testing.
* Device-location flow remains the default path for matchmaking.

### 3. Configure real dev/prod API URLs

Dev/prod base URLs must be configured before a real MVP build.

Pre-MVP requirement:

* Configure real dev/prod API URLs through Gradle properties or environment variables.
* Confirm local build still uses emulator-compatible backend URL.
* Confirm release builds do not allow cleartext traffic unless explicitly intended.

Acceptance criteria:

* Local flavor points to local backend.
* Dev flavor points to real dev backend.
* Prod flavor points to real prod backend.
* Release build does not accidentally target placeholder URLs.

### 4. Visual approval: require full visual review before approval

The visual approval screen currently allows approval once the partner profile is loaded.

If the product rule remains that users must actually review the visual profile before approving:

* Track whether all required photos were displayed or scrolled through.
* Disable the Approve action until the review condition is met.
* Keep Reject available without requiring full review, if desired.
* Add clear UI copy explaining why approval is blocked until review is complete.

Acceptance criteria:

* User cannot approve immediately without reviewing the visual profile.
* UI explains the requirement.
* The rule is local UI gating only; backend remains the final source of truth for match state.

### 5. Push notification client setup

Add the Android client-side foundation for push notifications.

Scope:

* Add Firebase Cloud Messaging dependency.
* Add Android notification permission handling for Android 13+.
* Add notification channel setup for Android 8+.
* Add client token retrieval.
* Add token refresh handling.
* Add foreground/background notification handling.
* Add notification tap routing.

Acceptance criteria:

* App can retrieve a current FCM registration token.
* App can handle token refresh.
* App can request notification permission contextually.
* App can receive foreground/background notification payloads.
* Notification taps can route to Home, first chat, visual approval, scheduling, or second chat.
* Stale notification taps safely refresh Home and do not crash.

### 6. Notification tap routing

Notification taps should not blindly open old local state.

Required behavior:

* Parse notification payload type.
* Open the app.
* Refresh backend session/Home state.
* Navigate only if the target entity is still actionable.
* Fall back to Home if the target is expired, closed, dismissed, or no longer visible.

Potential notification targets:

* First chat created.
* First chat message received.
* Visual review available.
* Visual approval mutual / connection created.
* Scheduling available.
* Partner submitted scheduling proposals.
* Scheduling confirmed.
* Second chat available.
* Second chat message received.
* Safety/report/penalty/account-state notifications if later needed.

Acceptance criteria:

* Expired notifications do not navigate to invalid screens.
* Logged-out users are sent to login.
* Deleted/pending-deletion accounts are handled through existing account states.
* Unknown notification types fall back to Home.

### 7. Polling strategy before push notifications

Current frontend behavior relies on polling:

* Home polling while there are actionable or pending states.
* Chat polling while a chat is active.
* Scheduling polling while negotiation is pending.
* Second-chat availability polling near scheduled availability.

Pre-MVP this is acceptable.

When push notifications are implemented:

* Keep polling as a fallback.
* Avoid duplicate user-facing events when both polling and push update the same state.
* Make Home refresh the source of truth after notification taps.
* Reduce aggressive polling only after push notifications are reliable.

Acceptance criteria:

* The app still works without push notifications.
* Notification taps refresh backend state before navigating.
* Polling and push do not produce conflicting UI states.

### 8. Remove or hide mock photo flows

The Android app still contains mock photo use cases and UI wiring for test flows.

Pre-MVP decision:

* Keep multipart upload as the official app flow.
* Hide or remove mock photo actions from production UI.
* Keep mock photo utilities only if useful in local/dev builds.

Acceptance criteria:

* Production users only see real file upload actions.
* Mock URLs are not exposed in production.
* Local/dev testing remains possible if needed.

### 9. Profile photo display robustness

Profile and visual-review screens should handle stored image URLs robustly.

Pre-MVP checks:

* Confirm uploaded photos render correctly in local/dev/prod.
* Confirm presigned/local URLs behave as expected on emulator and physical devices.
* Confirm failed image loads do not break the screen.
* Confirm visual profile photos are readable and ordered by position.

Acceptance criteria:

* Photos render in profile management.
* Photos render in visual approval.
* Broken/unreachable images show a safe fallback message.
* Photo ordering is stable.

### 10. Error copy and blocked states

Review user-facing error messages before MVP.

Focus areas:

* Active penalty.
* Active match limit.
* Active connection limit.
* Profile activation failures.
* Photo upload failures.
* Location permission denied.
* Second chat not available yet.
* Second chat expired.
* Account pending deletion.
* Account deletion finalized.

Acceptance criteria:

* Backend error codes are translated into understandable UI copy.
* User knows what action is possible next.
* Technical backend messages are not exposed when a clearer product message exists.

## Not Currently Implemented

### Push notifications

Push notifications are not implemented yet.

Required later:

* Firebase Cloud Messaging client dependency.
* Notification permission flow.
* Device token registration.
* Token refresh handling.
* Notification channel setup.
* Foreground/background message handling.
* Tap routing and state refresh.

### Social sign-in

Google Sign-In and other social providers are not implemented yet.

MVP decision:

* Defer social sign-in until after MVP.
* Keep email/password as the initial authentication path.
* Revisit after measuring onboarding friction.

Future implementation notes:

* Google Sign-In can still use Firebase Auth.
* The backend should continue receiving Firebase ID tokens, independent of whether the Firebase user authenticated with email/password or Google.
* Account-linking and duplicate-email behavior must be defined before enabling multiple auth providers.

### Real-time chat transport

Real-time chat via WebSocket or SSE is not implemented in Android.

Current behavior:

* Chat uses polling.

Future implementation:

* Keep polling as fallback.
* Replace or reduce polling only once real-time transport or push notifications are reliable.
