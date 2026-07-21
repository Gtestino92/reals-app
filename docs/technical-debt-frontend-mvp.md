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
- A terminally invalid Firebase user/session signs out locally and returns the root UI to Login.
- Transient token availability failures remain recoverable and do not force logout.
- Account deletion/reactivation flows still work with Firebase Auth and backend account state.

### 1.2 Profile photo upload path

MVP decision:
- Keep multipart profile-photo upload as the official frontend flow.
- Android uploads photos using real files and slot positions.
- Do not add temporary `isPersonPhoto` or `isFullBody` fields to the Android multipart upload request.
- Let the backend own photo validation and moderation results. Outside `prod`, backend provider `none` may use
  permissive MVP shortcut values; in `prod`, provider `none` no longer produces positive semantic/moderation facts.
- Remove mock/non-file URL photo flows from Android production code.

Acceptance criteria:
- Android uploads photos using multipart `file + position`.
- Android replaces photos using multipart file replacement.
- Android deletes photos through the backend delete endpoint.
- Production UI exposes only real file upload, replace, and delete actions.
- Mock URL/non-file photo actions are not exposed in production and are not part of the main Android profile flow.
- Profile activation can proceed once backend MVP photo validation behavior is in place.

### 1.3 Polling before push notifications

MVP decision:
- Keep Home status polling and chat/scheduling polling as the pre-notification fallback behavior.
- Android uses `GET /api/me/home/status` for silent Home polling and keeps `GET /api/me/home` as the source of truth for full Home rendering and routing.
- `GET /api/me/home/pending` is wired for lightweight pending state but is not the primary Android routing source.
- Push notifications are not required for MVP.
- Polling remains a temporary frontend/backend strategy for MVP validation.

Acceptance criteria:
- App remains usable without push notifications.
- Home, chat, scheduling and second-chat availability can refresh through polling.
- Silent Home polling fetches full Home only when status indicates a changed version or dirty state.
- Polling does not overwrite visible user errors or create confusing state jumps.
- Polling intervals are not unnecessarily aggressive.
- Repeated polling calls remain stable and idempotent.
- Payloads remain bounded enough for controlled MVP usage.

### 1.4 Admin/backoffice UI

MVP decision:
- Do not build a native Android admin/backoffice UI.
- Safety-report review can continue through backend/admin endpoints and Bruno or another external admin tool.
- Keep the backend-derived `priorityReview` field and priority ordering out of user-facing Android UI.

Acceptance criteria:
- Android does not expose admin-only surfaces.
- Admin/backoffice work remains backend/tooling-owned for MVP.

### 1.5 Chat safety reporting

MVP decision:
- First and second chat expose the existing `Reportar y cerrar chat` flow with an explicit safety-reason selector.
- `Seguridad de menores` sends `CHILD_SAFETY_CONCERN` through the existing chat safety-cancellation endpoint.
- Report details remain required and a submitted concern is not presented as a confirmed violation.

Acceptance criteria:
- The selector contains only inappropriate behavior, harassment, child safety and other.
- `NO_LONGER_INTERESTED` remains limited to non-safety exit behavior.
- Android does not add general safety-report or admin-review surfaces as part of this flow.

### 1.6 Dev-only helpers

MVP decision:
- Keep manual/dev-only helpers available in local/dev builds only.
- Production UI should not expose testing shortcuts.

Acceptance criteria:
- Dev-only helpers are hidden from production/release-like builds.
- Local/dev builds can still use controlled shortcuts when they materially speed up manual testing.

---

## 2. MVP frontend tasks

### 2.1 Complete RootViewModel refactor

Status:
- Mostly complete for MVP.
- `RealsRootViewModel` now primarily orchestrates feature coordinators/handlers.
- Remaining cleanup should be limited to small dependency/import cleanup or future feature-specific splits as needed.

Goal:
- Keep `RealsRootViewModel` as orchestration only.
- Move feature-specific logic into coordinators/handlers.
- Preserve current behavior during the refactor.

Expected structure:
- Session/account handling.
- Profile entry/profile operations.
- Home/matchmaking handling.
- First chat handling.
- Second chat handling.
- Visual approval handling.
- Partner profile handling.
- Scheduling handling.

Acceptance criteria:
- No behavior regression in login/provision/profile/home/chat/scheduling flows.
- Home auto-routing still works after queue/match creation.
- Silent polling still avoids overwriting visible UI errors.
- Local hidden Home interactions are still pruned when no longer relevant.
- Feature-specific behavior is no longer concentrated in `RealsRootViewModel`.


### 2.3 Configure real dev/prod API URLs

Dev/prod base URLs must be configured before generating real installable builds.

Status:
- Android now enforces HTTPS, non-local, non-placeholder dev/prod backend URLs at build time.
- Real hosted dev and prod API URLs are still external deployment prerequisites.

MVP requirement:
- Configure real dev/staging API URL through Gradle properties or environment variables.
- Confirm each hosted URL is reachable from device builds.

Acceptance criteria:
- Local flavor points to local backend.
- Dev flavor points to real dev/staging backend.
- Prod flavor does not point to placeholder URLs.
- Installable APKs for device testing can communicate with backend without local network hacks.
- Release-like builds do not allow cleartext traffic unless explicitly documented and intended.



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

Status:
- Android now expects flavor-specific files at `app/src/local/google-services.json`, `app/src/dev/google-services.json`,
  and `app/src/prod/google-services.json`.
- The expected Android clients are `com.reals.app.local`, `com.reals.app.dev`, and `com.reals.app`.
- Real Firebase Android App registration and secret injection remain operational prerequisites.

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
- Profile authenticity verification UX. In `prod`, backend provider `none` returns
  `AUTHENTICITY_VERIFICATION_NOT_CONFIGURED` instead of marking the profile authenticity verified; Android currently
  only maps the stable authenticity-verification error codes.
- Advanced onboarding experiments.
- Full production telemetry dashboards.
- Drag-and-drop profile-photo reordering.
- Generated photo thumbnails/previews.
- Direct Android-to-storage photo upload.
