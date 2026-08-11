# Frontend Technical Debt - Production

This file lists frontend technical debt, production hardening tasks and long-term Android product/architecture decisions that are not required for MVP, but should be revisited before wider production usage, store distribution, push notifications, stricter safety requirements or larger user volume.

Do not implement these implicitly while working on unrelated tasks.

---

## 1. Push notifications

Push notifications are partially implemented for the MVP Android client, but are not production-complete.

Production goal:
- Use Firebase Cloud Messaging to notify users about time-sensitive interactions while keeping backend Home state
  authoritative.
- Reduce polling only after production delivery/open reliability is measured.

Implemented or substantially implemented:
- Firebase Cloud Messaging dependency and Android service are present.
- Android notification permission handling exists for Android 13+.
- Notification channel creation exists for Android 8+.
- The app retrieves the current FCM registration token after active session bootstrap when possible.
- Token registration and refresh handling send the Android token to `PUT /api/me/push-tokens`.
- Supported notification contracts include visual-review available/reminder, scheduling available/proposals/confirmed,
  and second-chat reminder.
- Notification taps open the app and refresh authoritative backend/Home state instead of trusting stale local payload
  targets.
- Home polling remains the fallback source of truth.
- A representative visual-review reminder delivery and notification open recovery path passed the July 21, 2026 signed,
  optimized `localRelease` smoke.

Remaining production work:
- Broaden notification coverage across all intended product events.
- Verify production Play Integrity, Google Play distribution, remote HTTPS deployment, and production-device delivery
  conditions; the local App Check-disabled smoke is not evidence for Play Integrity.
- Add notification delivery/open observability without logging tokens, private payloads, or sensitive identifiers.
- Harden stale registration-token lifecycle handling in coordination with backend cleanup/disable semantics.
- Make foreground/background notification presentation consistent.
- Deliberately choose between mixed `notification + data` FCM payloads and `data-only` payloads.
- Test broader device/manufacturer/background-restriction behavior.
- Review lock-screen privacy for notification content.
- Reduce polling only when production evidence shows push delivery/open behavior is reliable enough.

Still-valid acceptance criteria:
- App can retrieve a current FCM registration token.
- App can send token registration/refresh to backend.
- App can request notification permission contextually.
- App can receive supported foreground/background notification payloads.
- App can handle notification taps safely.
- Polling and push do not produce conflicting UI states.

Notification presentation limitation:
- The backend can send a mixed FCM payload containing both display notification fields and data.
- Android also constructs a local notification for recognized data types.
- Foreground and background delivery paths can therefore use different title/body copy.
- This did not block the July 21, 2026 local release smoke.
- Future work should either unify copy across both paths or adopt a single presentation owner after an explicit contract
  decision.

Potential notification targets:
- First chat created.
- First chat message received.
- Visual review available.
- Visual approval mutual / connection created.
- Scheduling available.
- Partner submitted scheduling proposals.
- Scheduling confirmed.
- Second chat available.
- Second chat message received.
- Safety/report/penalty/account-state notifications if later needed.

---

## 2. Notification tap routing

Notification taps should not blindly open old local state.

Current behavior:
- Supported notification taps open the app and trigger a Home refresh.
- If the app is already in a feature flow, supported notification opens return through Home so backend state is reloaded.
- Unknown notification types are ignored or fall back to normal app state instead of navigating from untrusted payload
  data.

Required behavior:
- Parse notification payload type.
- Open the app.
- Refresh backend session/Home state.
- Navigate only if the target entity is still actionable.
- Fall back to Home if the target is expired, closed, dismissed, hidden or no longer visible.

Acceptance criteria:
- Expired notifications do not navigate to invalid screens.
- Logged-out users are sent to login.
- Deleted/pending-deletion accounts are handled through existing account states.
- Unknown notification types fall back to Home.
- Notification taps do not trust stale local state.

---

## 3. Polling strategy after push notifications

Current behavior:
- Home silent polling uses `GET /api/me/home/status` and fetches full `GET /api/me/home` only when the status version changes or Home is dirty.
- Full `GET /api/me/home` remains the Android source of truth for Home rendering, routing, explicit refreshes and notification taps.
- `GET /api/me/home/pending` is wired in the Android data/domain layer but is not the primary routing source.
- Chat, scheduling and second-chat availability use polling.

Production target:
- Keep polling as fallback.
- Reduce aggressive polling once push notifications are reliable.
- Avoid duplicate user-facing events when both polling and push update the same state.
- Make Home refresh the source of truth after notification taps.

Acceptance criteria:
- App still works if push notifications are delayed, disabled or denied.
- Polling and push do not produce conflicting UI states.
- Backend read load decreases compared with pure polling.

---

## 4. Real-time chat transport

Real-time chat via WebSocket or SSE is not implemented in Android.

Current behavior:
- Chat uses polling.

Production options:
- Continue with polling + push notifications if sufficient.
- Add WebSocket.
- Add Server-Sent Events.
- Use a managed realtime service.

If implemented:
- Keep polling or REST refresh as fallback.
- Define reconnect behavior.
- Define missed-message recovery.
- Avoid relying on a single app instance’s in-memory state.
- Coordinate with backend shared pub/sub or managed realtime infrastructure.

Acceptance criteria:
- Message delivery is reliable after reconnect.
- App can recover missed messages.
- Chat state remains consistent with backend persisted messages.

---

## 5. Social authentication providers

Google Sign-In and other social authentication providers are deferred until after MVP.

Production decision:
- Revisit after measuring onboarding friction and target audience expectations.

Future frontend scope:
- Add Google Sign-In through Firebase Auth.
- Update onboarding copy to support multiple sign-in methods.
- Handle account-linking edge cases.
- Handle duplicate email/provider conflicts.
- Coordinate with backend behavior, which should continue to receive Firebase ID tokens regardless of provider.

Acceptance criteria:
- Existing email/password accounts are not broken.
- A user signing in with Google and an email/password account using the same email has a defined path.
- Sign-out/delete/reactivation flows still work across providers.

---

## 6. Firebase email verification UX

Production decision pending:
- Whether email verification is required for provisioning, profile activation or continued account usage.

If backend enforces `EMAIL_NOT_VERIFIED`, frontend must support:
- clear blocked-state copy;
- resend verification email;
- refresh token/session after user verifies email;
- retry provisioning or activation.

Acceptance criteria:
- User understands why they are blocked.
- User can request a new verification email.
- App can recover after verification without reinstalling or clearing data.

---

## 7. Visual content reporting and moderation UX

MVP may defer full visual-profile reporting, but production should support it.
The existing first- and second-chat `Reportar y cerrar chat` flow already requires details and lets users select
inappropriate behavior, harassment, child safety or other. That chat-only support does not cover the visual
surfaces below and does not consume the general `/api/safety/reports` endpoint.

Future frontend scope:
- Report profile.
- Report individual photo.
- Select a report reason on visual/profile/photo reporting surfaces.
- Add optional details.
- Submit to backend moderation endpoint.
- Optionally cancel/reject current flow after report.
- Confirm report submission.

Surfaces:
- Visual approval screen.
- Partner profile screen.
- Future photo viewer.
- Possibly Home active interaction cards.

Acceptance criteria:
- User can report objectionable visual content without needing to send a chat message.
- Report flow does not leave the user stuck.
- UI clearly distinguishes “report only” from “report and cancel/reject”.

---

## 8. Production media UX

Future work:
- Better loading states for photos.
- Better broken-image fallback.
- Thumbnail support if backend provides thumbnails.
- Caching policy review.
- Avoid treating technically decoded photos as semantically valid person/full-body photos. In `prod`, backend
  provider `none` can return `isPersonPhoto=false`, `isFullBody=false`, `validationStatus=PENDING` and
  `moderationStatus=NEEDS_REVIEW`; Android should continue to present backend state without inventing local trust.
- Avoid showing unvalidated/hidden/rejected photos if backend later introduces stricter visibility rules.
- Add graceful handling for expired presigned URLs.
- Retry image loading where appropriate.

Acceptance criteria:
- Visual profiles load acceptably on mobile networks.
- Expired or broken image URLs do not break the screen.
- User can refresh/retry if image loading fails.

---

## 9. Profile authenticity verification UX

Profile authenticity verification is separate from profile photos and photo moderation, and is deferred until after
MVP. It is not legal identity, document or age verification.

Current Android state:
- Android reads `authenticityVerified` and `authenticityVerificationStatus` from profile responses.
- Android maps backend authenticity-verification error codes to user-facing copy.
- Android does not declare or expose `POST /api/me/profile/authenticity-verification` yet.

Future frontend scope:
- Add entry point for verification if product decides it is required or optional.
- Support provider flow:
  - selfie;
  - liveness;
  - live reference capture;
  - provider-neutral facial comparison status;
  - external provider redirect/SDK;
  - status polling.
- Show verification state:
  - not started;
  - pending;
  - verified;
  - failed;
  - retry allowed;
  - locked/manual review.
- Handle failures and retries.

Acceptance criteria:
- Verification flow does not block unrelated MVP flows unless product decides it should.
- User understands current verification status.
- App can recover from provider cancellation/failure.

---

## 10. Production environment and release management

Implemented:
- Android environment isolation now gives `local`, `dev`, and `prod` distinct application IDs, labels, Firebase config
  locations, App Check behavior, and cleartext policies.
- Build-time validation rejects dev/prod cleartext, local-only hosts, and placeholder backend URLs.
- Release builds enable R8 code shrinking, optimization, obfuscation, and resource shrinking.
- CI builds and inspects optimized `localRelease`, uploads its APK and exact mapping file, and skips dev/prod release
  validation unless real Firebase, URL, and signing prerequisites are present.
- The July 21, 2026 manually installed, signed, optimized `localRelease` APK smoke passed for exercised MVP runtime
  paths; current local builds disable App Check.

Future work:
- Configure real production API URL.
- Create and supply real production Firebase Android App configuration.
- Sign release builds with production keystore.
- Define app versioning strategy.
- Define test track/release process if Play Store is used.
- Execute and record `devRelease` and `prodRelease` smokes against real Firebase, real HTTPS backend URLs, Play
  Integrity, production signing/distribution prerequisites, and representative production devices.

Acceptance criteria:
- Dev builds cannot accidentally target production.
- Production builds cannot accidentally target dev/staging.
- Production Firebase config is sourced from the production Firebase project.
- Release signing is reproducible and documented.
- Version names/codes are managed consistently.
- Each distributed release retains its exact `mapping.txt` for retrace.

---

## 11. Store distribution readiness

Before Play Store distribution:
- Review Google Play user-generated content requirements.
- Provide report/block mechanisms where required.
- Ensure privacy policy and data safety information are accurate.
- Review permissions:
  - location;
  - notifications;
  - media/photo access.
- Ensure permission prompts are contextual and explain the need.
- Avoid exposing dev/test UI in production builds.

Acceptance criteria:
- App does not expose manual dev location fallback.
- App explains permissions.
- App has a basic path for objectionable content reporting.
- Production build configuration is clean.

---

## 12. Observability and diagnostics

Future frontend observability:
- Add non-sensitive crash reporting.
- Add non-sensitive analytics/events for critical funnel steps.
- Track API error code frequency.
- Track notification delivery/open behavior if implemented.
- Track onboarding and activation drop-off.
- Track chat/scheduling flow drop-off.

Privacy constraint:
- Do not log chat contents, personal messages, tokens, private media URLs, full emails or raw sensitive payloads.

Useful events:
- Sign-up started/completed.
- Provision completed/failed.
- Profile created.
- Photo upload success/failure.
- Profile activation success/failure.
- Queue joined/left.
- Match found.
- First chat opened/completed/rejected.
- Visual review opened/approved/rejected.
- Scheduling proposals submitted/accepted/rejected.
- Second chat opened/completed/expired.
- Account deletion/reactivation.

---

## 13. Error handling and resilience

Future hardening:
- Standardize API error mapping in Android.
- Add retry strategy for transient network errors.
- Avoid retrying non-idempotent actions blindly.
- Add offline/poor-network UX.
- Add better timeout/cancellation handling.
- Add state refresh after app returns from background.
- Add stale-action protection for buttons that depend on backend state.

Acceptance criteria:
- User-facing errors are understandable.
- Transient failures can be retried safely.
- Stale screens recover by refreshing backend state.

---

## 14. Scheduling authoritative clock

Current Android correction:
- Scheduling proposal slot selection uses the screen's reactive Android device time instead of freezing `now` when
  the selector enters composition.
- The selector re-derives the current system time zone on recomposition instead of caching it permanently.
- Selected proposal slots are revalidated as device time advances, and expired selected slots block submission until
  the user removes or replaces them.

Remaining production limitation:
- Future/past validation still depends on the correctness of the Android device or emulator wall clock.

Recommended production hardening:
- Scheduling/negotiation responses should expose an authoritative `serverTime`, or the networking layer should
  consistently retain a trusted HTTP server date.
- Android should store the received server instant and `SystemClock.elapsedRealtime()` at the moment it was received.
- Current server time should be estimated as `receivedServerInstant + monotonicElapsedDuration`.
- Device time zone should be used only to display local dates and times.
- Future/past proposal validation should use the authoritative estimated server instant.
- The estimate should be refreshed during negotiation refreshes and before scheduling mutations.
- Clock skew beyond a defined tolerance should be logged and surfaced with actionable UX.

Priority:
- Production hardening, not required for the current MVP correction.
- Requires backend/API or shared networking-time policy work; no OpenAPI change is implemented by the current Android
  fix.

---

## 15. Accessibility and UX polish

Future work:
- Accessibility labels for major actions.
- Content descriptions for images/buttons.
- Font scaling checks.
- Color contrast checks.
- Loading/empty/error states for all core screens.
- Better disabled-button explanations.
- Better form validation feedback.

Not MVP blocker, but recommended before wider production use.

---

## 16. Android performance

Future work:
- Review recomposition hotspots after RootViewModel refactor.
- Avoid unnecessarily large state objects.
- Ensure image loading is efficient.
- Avoid polling when screen is not active.
- Avoid leaking coroutines or stale polling jobs.
- Confirm lifecycle-aware collection for flows/state.

Acceptance criteria:
- App remains responsive on mid-range devices.
- Polling stops when screen is not active.
- Large profile/photo/chat states do not cause visible jank.

---

## 17. Security and privacy hardening

Future frontend work:
- Avoid logging tokens and sensitive payloads.
- Review local persistence of user/session state.
- Ensure sign-out clears sensitive local state.
- Ensure deleted-account states do not keep actionable local state.
- Consider screen privacy or sensitive-content behavior only if product requires it.
- Review how notification content appears on lock screen.

Acceptance criteria:
- Sensitive data is not exposed through logs.
- Stale local state does not allow navigation into deleted/invalid sessions.
- Notification content does not reveal more than intended.

Implemented hardening:
- A cached Firebase user that becomes terminally invalid is signed out locally and routed to Login.
- Generic token availability failures remain recoverable; recoverable backend account deletion remains a distinct
  root state.

---

## 18. Future product experiments

Deferred:
- Reveal quotas.
- Advanced onboarding variants.
- Guided question experiments.
- Compatibility/affinity explanation UI.
- Trust/safety badges, if ever desired.
- Premium/monetization UI, if product direction changes.
- Explicit “share my contact” / contact-card feature for second chat only, chosen by the user, likely requiring backend
  and Android `CONTACT` message type/contract support; the recipient should eventually be able to copy the phone number
  or open Android's contact-save flow, with no calling action required.

Constraint:
- Do not introduce attractiveness, popularity or ELO-style ranking UI unless product principles change explicitly.

---

## Profile photo thumbnails

Current profile photo grid loads the same presigned image URL used for full-size profile photos. Android now uses stable Coil cache keys that ignore changing presigned query parameters, which improves cache reuse, but the app can still download full-size image bytes for small grid slots.

Future production improvement:
- Backend should generate and store thumbnail variants on upload/replace.
- `PhotoResponse` should expose both:
  - `url` for full-size/profile detail use;
  - `thumbnailUrl` for grid/list use.
- Android `PhotoGrid` should load `thumbnailUrl`.
- Full-size/profile detail screens should continue using `url`.
- This should reduce bandwidth, improve grid load speed, and reduce memory pressure without degrading full-size image quality.

Priority:
- Not required for local MVP.
- Recommended before broader beta/production usage.
