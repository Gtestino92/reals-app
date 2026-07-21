# Frontend Security Notes

## XSS And User Text

This app is native Android with Jetpack Compose. It does not render backend or user-provided content through `WebView`, `Html.fromHtml`, Markdown, or injected JavaScript. User text is rendered as plain Compose `Text`.

The frontend still normalizes text before sending it to the backend:

- Profile fields strip unsafe invisible control characters.
- Single-line fields collapse whitespace and remove line breaks.
- Bio, chat messages, and safety report details preserve normal line breaks.
- HTML-like tags are rejected in user-editable text.
- User-editable text has explicit frontend length limits.

This is defense in depth only. The backend must continue to validate, store, and encode user content safely for every consumer, especially if a future web frontend or admin dashboard renders the same data.

## Current XSS-Sensitive Surfaces

- No `WebView`.
- No HTML rendering APIs.
- No Markdown/rich-text rendering.
- Image URLs from the backend are loaded as images only, not executed as script.

If any future feature adds `WebView`, rich text, Markdown, or external link opening, it must add an allowlist and sanitization strategy before rendering remote/user content.

## Validation Ownership

Frontend validation is for immediate UX and risk reduction. Backend validation remains authoritative and must not rely on frontend checks.

## Firebase App Check

Android sends Firebase App Check tokens to the Reals backend with `X-Firebase-AppCheck`. This helps the backend reject
requests that do not come from a registered app installation, but it is not a user-authentication or authorization
mechanism.

App Check limitations:

- It does not replace Firebase Authentication, backend authorization, rate limiting, TLS or request validation.
- It does not prove that user content is safe or that a user is allowed to perform an action.
- Replay protection and limited-use tokens are intentionally deferred for this MVP.
- Debug provider secrets must never be committed, copied into docs, embedded in `BuildConfig`, stored in Gradle
  properties or logged.

If a local debug secret is exposed, revoke it in Firebase Console and register a new one. Do not weaken backend JWT
verification for debug tokens; Firebase still issues normal App Check JWTs after a registered debug secret is accepted.

Provider isolation is compile-time flavor-specific:

- `local` uses `DebugAppCheckProviderFactory` and installs as `com.reals.app.local`.
- `dev` uses `PlayIntegrityAppCheckProviderFactory` and installs as `com.reals.app.dev`.
- `prod` uses `PlayIntegrityAppCheckProviderFactory` and installs as `com.reals.app`.

Backend allowlists must use Firebase App IDs from the matching Firebase Android App, not Android package names alone.
The debug App Check dependency must remain local-only.

## Android Network Boundaries

The `local` flavor permits cleartext only for local Android development hosts needed by the backend and MinIO ADB
reverse workflow. The `dev` and `prod` flavors prohibit cleartext traffic and reject localhost, loopback,
emulator-only, and placeholder backend URLs at build time.

## Android Backup

The Android app sets `android:allowBackup="false"` and does not declare backup or data-extraction rules. Reals intentionally disables Android cloud backup and device-transfer backup for application-managed data because dating-profile, session-related and cached media state are sensitive.
