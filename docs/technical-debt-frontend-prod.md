# Frontend Technical Debt - Production

This file lists frontend technical debt, production hardening tasks and long-term Android product/architecture decisions that are not required for MVP, but should be revisited before wider production usage, store distribution, push notifications, stricter safety requirements or larger user volume.

Do not implement these implicitly while working on unrelated tasks.

---

## 1. Push notifications

Push notifications are not implemented yet.

Production goal:
- Add Firebase Cloud Messaging support to reduce polling and notify users about time-sensitive interactions.

Required client work:
- Add Firebase Cloud Messaging dependency.
- Add Android notification permission handling for Android 13+.
- Add notification channel setup for Android 8+.
- Add client token retrieval.
- Add token refresh handling.
- Add foreground/background message handling.
- Add notification tap routing.
- Add stale-notification handling.

Acceptance criteria:
- App can retrieve a current FCM registration token.
- App can send token registration/refresh to backend.
- App can request notification permission contextually.
- App can receive foreground/background notification payloads.
- App can handle notification taps safely.

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

Future frontend scope:
- Report profile.
- Report individual photo.
- Select report reason.
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
- Avoid showing unvalidated/hidden/rejected photos if backend later introduces moderation states.
- Add graceful handling for expired presigned URLs.
- Retry image loading where appropriate.

Acceptance criteria:
- Visual profiles load acceptably on mobile networks.
- Expired or broken image URLs do not break the screen.
- User can refresh/retry if image loading fails.

---

## 9. Identity verification UX

Identity verification is separate from profile photos and is deferred until after MVP.

Future frontend scope:
- Add entry point for verification if product decides it is required or optional.
- Support provider flow:
  - selfie;
  - liveness;
  - document capture;
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

Future work:
- Configure real production API URL.
- Separate local/dev/prod Firebase configuration clearly.
- Ensure production release builds do not allow cleartext traffic.
- Sign release builds with production keystore.
- Define app versioning strategy.
- Define test track/release process if Play Store is used.

Acceptance criteria:
- Dev builds cannot accidentally target production.
- Production builds cannot accidentally target dev/staging.
- Release signing is reproducible and documented.
- Version names/codes are managed consistently.

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

## 14. Accessibility and UX polish

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

## 15. Android performance

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

## 16. Security and privacy hardening

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

---

## 17. Future product experiments

Deferred:
- Reveal quotas.
- Advanced onboarding variants.
- Guided question experiments.
- Compatibility/affinity explanation UI.
- Trust/safety badges, if ever desired.
- Premium/monetization UI, if product direction changes.

Constraint:
- Do not introduce attractiveness, popularity or ELO-style ranking UI unless product principles change explicitly.
