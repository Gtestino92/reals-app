# Frontend Technical Debt - MVP

This file lists frontend technical debt, cleanup tasks and product decisions that should be resolved or explicitly accepted before a first usable MVP/beta version of the Android app.

MVP scope here means: enough to run the core product flow end-to-end with controlled users, installable APKs, a dev/staging backend, and known temporary shortcuts clearly documented.

Do not implement these implicitly while working on unrelated tasks.

---

## 1. MVP product decisions

### 1.1 Authentication path

MVP decision:
- Keep Firebase email/password authentication as the only supported sign-up/sign-in path.
- Defer Google Sign-In and other social providers until after MVP unless onboarding friction becomes a blocker.

Acceptance criteria:
- Email/password sign-up works.
- Email/password sign-in works.
- Sign-out works.
- Account deletion/reactivation flows still work with Firebase Auth and backend account state.

### 1.2 Profile photo upload path

MVP decision:
- Keep multipart profile-photo upload as the official frontend flow.
- Do not add temporary `isPersonPhoto` or `isFullBody` fields to the Android multipart upload request.
- Let the backend use permissive pre-MVP validation defaults for uploaded photos.

Acceptance criteria:
- Android uploads photos using file + position.
- Production UI does not expose mock URL photo flows.
- Profile activation can proceed once backend MVP photo validation behavior is in place.

### 1.3 Polling before push notifications

MVP decision:
- Keep Home polling and chat/scheduling polling as the pre-notification fallback behavior.
- Push notifications are not required for MVP.

Acceptance criteria:
- App remains usable without push notifications.
- Home, chat, scheduling and second-chat availability can refresh through polling.
- Polling does not overwrite visible user errors or create confusing state jumps.

### 1.4 Admin/backoffice UI

MVP decision:
- Do not build a native Android admin/backoffice UI.
- Safety-report review can continue through backend/admin endpoints and Bruno or another external admin tool.

### 1.5 Dev-only helpers

MVP decision:
- Keep manual/dev-only helpers available in local/dev builds only.
- Production UI should not expose testing shortcuts.

---

## 2. MVP frontend tasks

### 2.1 Complete RootViewModel refactor

Status:
- In progress / assumed before further frontend hardening.

Goal:
- Keep `RealsRootViewModel` as orchestration only.
- Move feature-specific logic into coordinators/handlers.
- Preserve current behavior during the refactor.

Expected structure:
- Session/account handling.
- Profile handling.
- Home/matchmaking handling.
- First chat handling.
- Visual approval handling.
- Scheduling handling.
- Second chat handling.

Acceptance criteria:
- No behavior regression in login/provision/profile/home/chat/scheduling flows.
- Home auto-routing still works after queue/match creation.
- Silent polling still avoids overwriting visible UI errors.
- Local hidden Home interactions are still pruned when no longer relevant.

### 2.2 Hide manual location fallback outside local/dev

The Home screen currently exposes a manual latitude/longitude fallback intended for development/testing.

MVP requirement:
- Hide manual coordinate entry in production builds.
- Keep it available only in local/debug/dev builds or behind a feature flag.
- The user-facing production flow should use device location permission and current device location.

Acceptance criteria:
- Production UI does not show manual coordinate entry.
- Local/dev builds can still manually enter coordinates for testing.
- Device-location flow remains the default path for matchmaking.

### 2.3 Configure real dev/prod API URLs

Dev/prod base URLs must be configured before generating real installable builds.

MVP requirement:
- Configure real dev/staging API URL through Gradle properties or environment variables.
- Confirm local build still uses emulator-compatible backend URL.
- Confirm release builds do not allow cleartext traffic unless explicitly intended.
- Avoid placeholder URLs in release-like builds.

Acceptance criteria:
- Local flavor points to local backend.
- Dev flavor points to real dev/staging backend.
- Prod flavor does not point to placeholder URLs.
- Installable APKs for device testing can communicate with backend without local network hacks.

### 2.4 Visual approval: require full visual review before approval

The visual approval screen currently allows approval once the partner visual profile is loaded.

MVP product decision:
- Decide whether users must actually review all required photos before approving.

If the rule remains active:
- Track whether all required photos were displayed or scrolled through.
- Disable the Approve action until the review condition is met.
- Keep Reject available without requiring full review, if desired.
- Add clear UI copy explaining why approval is blocked.

Acceptance criteria:
- User cannot approve immediately without reviewing the visual profile if the rule is enabled.
- UI explains the requirement.
- Backend remains the final source of truth for match state.

### 2.5 Remove or hide mock photo flows

The Android app still contains mock photo use cases and UI wiring for test flows.

MVP decision:
- Keep multipart upload as the official app flow.
- Hide or remove mock photo actions from production UI.
- Keep mock photo utilities only if useful in local/dev builds.

Acceptance criteria:
- Production users only see real file upload actions.
- Mock URLs are not exposed in production.
- Local/dev testing remains possible if needed.

### 2.6 Profile photo display robustness

Profile and visual-review screens should handle stored image URLs robustly.

MVP checks:
- Confirm uploaded photos render correctly in local/dev/staging.
- Confirm presigned/local URLs behave as expected on emulator and physical devices.
- Confirm failed image loads do not break the screen.
- Confirm visual profile photos are readable and ordered by position.

Acceptance criteria:
- Photos render in profile management.
- Photos render in visual approval.
- Broken/unreachable images show a safe fallback message.
- Photo ordering is stable.

### 2.7 Error copy and blocked states

Review user-facing error messages before MVP.

Focus areas:
- Active penalty.
- Active match limit.
- Active connection limit.
- Profile activation failures.
- Photo upload failures.
- Location permission denied.
- Second chat not available yet.
- Second chat expired.
- Account pending deletion.
- Account deletion finalized.

Acceptance criteria:
- Backend error codes are translated into understandable UI copy.
- User knows what action is possible next.
- Technical backend messages are not exposed when a clearer product message exists.

### 2.8 Report visual profile or profile photo

Current state:
- The app supports safety/report flows from chat contexts.
- Visual profile review can expose user-generated photos before or after a connection is created.

MVP safety decision:
- Automatic image moderation is not required for MVP.
- If not implemented before MVP, reporting visual profile/photo content must remain documented as a known safety gap.

Recommended MVP implementation:
- Add “Report profile” or “Report photo” action from:
  - visual approval screen;
  - partner profile screen;
  - any future profile/photo viewer.
- Allow reason selection and optional details.
- Route the report to backend moderation/safety endpoints.
- Make clear whether report also rejects/cancels the current flow or only submits a moderation report.

Acceptance criteria:
- User can report problematic visual content without needing to send a chat message.
- Reporting does not crash or leave the user stuck in the interaction.
- UI makes clear what happens to the current interaction after reporting.

---

## 3. MVP installable build readiness

### 3.1 APK/device testing

MVP requirement:
- Support installable APK builds for physical-device testing outside Play Store.
- Prefer dev flavor against a real dev/staging backend for device testing.

Acceptance criteria:
- Dev APK points to a reachable backend.
- Local APK still works with emulator/local backend.
- Physical devices do not depend on `10.0.2.2`.
- Release-like APKs are signed when needed for sharing with testers.

### 3.2 Firebase Android configuration

MVP requirement:
- Validate Firebase Auth configuration for each intended build flavor/environment.
- Ensure `google-services.json` and Firebase project settings match the Android package/application IDs used for MVP builds.

Acceptance criteria:
- Email/password auth works on local/dev APKs.
- ID token provisioning works against backend.
- Deleted-account and reactivation flows behave correctly.

---

## 4. Explicitly deferred from MVP

The following are not MVP blockers:

- Firebase Cloud Messaging / push notifications.
- Notification tap routing.
- Google Sign-In and other social auth providers.
- Real-time chat via WebSocket or SSE.
- Native Android admin/backoffice UI.
- Strict automatic profile-photo moderation.
- Identity verification UX.
- Advanced onboarding experiments.
- Full production telemetry dashboards.
