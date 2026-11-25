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

Android sends Firebase App Check tokens to the Reals backend with `X-Firebase-AppCheck` in App Check-enabled
environments. This helps the backend reject requests that do not come from a registered app installation, but it is not a
user-authentication or authorization mechanism. The Android `local` flavor disables App Check completely and relies on
the backend `local-firebase` profile where App Check enforcement is disabled.

App Check limitations:

- It does not replace Firebase Authentication, backend authorization, rate limiting, TLS or request validation.
- It does not prove that user content is safe or that a user is allowed to perform an action.
- Replay protection and limited-use tokens are intentionally deferred for this MVP.
- Debug provider secrets for enabled debug variants must never be committed, copied into docs, embedded in
  `BuildConfig`, stored in Gradle properties or logged.

If a debug secret is exposed, revoke it in Firebase Console and register a new one. Do not weaken backend JWT
verification for debug tokens; Firebase still issues normal App Check JWTs after a registered debug secret is accepted.

Provider isolation is compile-time variant-specific:

- `localDebug` and `localRelease` disable App Check and install no debug or Play Integrity provider.
- `devDebug` uses `DebugAppCheckProviderFactory` and installs as `com.reals.app.dev`.
- `devRelease` uses `PlayIntegrityAppCheckProviderFactory` and installs as `com.reals.app.dev`.
- `prodDebug` uses `DebugAppCheckProviderFactory` and installs as `com.reals.app`.
- `prodRelease` uses `PlayIntegrityAppCheckProviderFactory` and installs as `com.reals.app`.

Backend allowlists for App Check-enabled environments must use Firebase App IDs from the matching Firebase Android App,
not Android package names alone.
Debug App Check tokens are developer-machine/device credentials. Never commit them, paste them into documentation, expose
them in CI logs, embed them in Gradle properties or store them in `BuildConfig`.

## Android Network Boundaries

The `local` flavor permits cleartext only for local Android development hosts needed by the backend and MinIO ADB
reverse workflow. The `dev` and `prod` flavors prohibit cleartext traffic and reject localhost, loopback,
emulator-only, and placeholder backend URLs at build time.

## Participant chat audio

Deploy the history-header backend before the Android client. Every successful participant history GET, including an empty incremental page, carries `Cache-Control: no-store` and one `X-Reals-Counterpart-Audio-Access` value. The authenticated Android API client has no configured OkHttp response cache. The history endpoint streams the raw body so Android can apply the header before reading JSON. It uses the ordinary Firebase, App Check and invalid-token refresh path.

| Input or transition | First Chat and Second Chat behavior |
| --- | --- |
| One exact `ALLOWED` value | Fresh history rows can provide counterpart audio while the chat is readable. A bounded or empty page can omit an already authorized message. URL renewal requires the target in the response. |
| Missing, duplicate, combined or unrecognized value | Access becomes UNKNOWN. Remove previous counterpart sources. Recovery requires a subsequent authorized read and a fresh source for each message. |
| One exact `RESTRICTED` value or an owned HTTP 409 `USER_PAIR_BLOCKED` | Retire counterpart audio immediately. The decision survives decoding errors, delayed coordinator results, mutations and later ALLOWED headers for this chat entry. Existing lifecycle rules govern navigation, text and the participant's own audio. |
| Withdrawal during playback or composition | The access owner signals the playback session even when Compose frames are suspended. The session releases the affected player, invalidates its generation, cancels renewal and rejects obsolete callbacks. Confirmed quotes and locked send payloads survive. A newly composed reply cannot target withdrawn audio. |
| Session or chat replacement | Each entry owns a separate history generation. History decisions and playable sources stay in memory. |

Withdrawn rows keep the server ID, chat ID, sender, type and time needed for ordering, cursor advancement and sender boundaries. The message list excludes them from bubbles and actions. A HEART response can confirm reaction metadata on its original target. History reads supply media capabilities.

Focused JVM tests exercise Retrofit headers, malformed bodies, history and mutation races, and renewal in both chats. A Pixel 8 API 36 emulator instrumentation test exercised the Compose session with a recording player adapter and verified release during preparation, playback and pause. Physical-device playback and hosted two-participant validation remain outstanding. A previously issued signed URL can remain usable outside the app until it expires.

## Release Obfuscation

Release builds use R8 code shrinking, optimization, obfuscation, and resource shrinking. This is defense in depth and a
code-size/runtime optimization measure. It is not an authentication, authorization, App Check, TLS, backend-validation,
anti-tamper, or secret-protection boundary.

Do not put secrets in Android code or resources because obfuscation cannot protect them. Keep Firebase configs,
keystores, signing passwords, App Check debug tokens, backend service credentials, and deployment credentials outside
source control and outside uploaded build artifacts.

## Android Backup

The Android app sets `android:allowBackup="false"` and does not declare backup or data-extraction rules. Reals intentionally disables Android cloud backup and device-transfer backup for application-managed data because dating-profile, session-related and cached media state are sensitive.
