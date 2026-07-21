# Local Development

## Prerequisites

- Android Studio or IntelliJ IDEA with Android support.
- JDK compatible with the Gradle Android plugin used by the project.
- Android SDK for compile/target SDK configured in `app/build.gradle.kts`.
- Firebase configuration if testing Firebase Auth or FCM.

## Flavors

The app has one environment flavor dimension:

- `local`: local backend through ADB reverse. Default base URL is `http://127.0.0.1:8080/`; cleartext traffic is allowed.
- `dev`: dev/staging backend. Set `realsDevBaseUrl` or `REALS_DEV_BASE_URL`.
- `prod`: production backend. Set `realsProdBaseUrl` or `REALS_PROD_BASE_URL`.

The local base URL can be overridden with Gradle property `realsLocalBaseUrl` or environment variable `REALS_LOCAL_BASE_URL`.

The `local` flavor enables `ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION`.
`dev` and `prod` keep it disabled and continue to require the normal Firebase
email-link verification flow.

## Firebase

The Google Services plugin is applied only when one of these files exists:

- `app/google-services.json`
- `app/src/local/google-services.json`
- `app/src/dev/google-services.json`
- `app/src/prod/google-services.json`

Firebase Auth is required for real sign-in/provisioning flows. Push notification testing requires FCM configuration and Android notification permission on Android 13+.

## Firebase App Check

App Check is installed before the application container is created, so API repositories use it from startup. The
provider is selected by source set:

- `local`: debug provider.
- `dev`: Play Integrity.
- `prod`: Play Integrity.

All requests made through the Reals Retrofit client include:

```http
X-Firebase-AppCheck: <token>
```

The token is not added to URLs, query parameters, cookies or request bodies. The OkHttp logger redacts the header.

### Local debug provider

Run the `local` flavor with a valid Firebase configuration and let the Firebase SDK print the generated debug secret
for that developer machine/device. Register that secret in Firebase Console under App Check for the Android app, then
retry the app. Never commit or paste the debug secret into repository files, examples, Gradle properties or
`BuildConfig`. If a debug secret is exposed, revoke it in Firebase Console and generate/register a new one.

The backend must still verify App Check JWTs normally. A registered debug secret allows Firebase to issue a normal App
Check token; it is not a reason to disable JWT verification.

If Firebase Console requires registering the Android app with a SHA-256 fingerprint even for local setup, use the
`localDebug` signing certificate from the local Android debug keystore. On this Windows development machine the
standard debug keystore path is:

```text
C:\Users\Administrador\.android\debug.keystore
```

Get the exact `localDebug` fingerprint with:

```powershell
.\gradlew.bat :app:signingReport --no-daemon --console=plain
```

Copy the `SHA-256` value for `Variant: localDebug` / `Config: debug`. The SHA-256 fingerprint is not a secret, but the
App Check debug token printed by `DebugAppCheckProvider` is a secret and must not be committed.

### Play Integrity providers

For `dev` and `prod`, register the corresponding Firebase Android app for App Check with Play Integrity. The Firebase
project, `google-services.json`, package name and linked Play Integrity configuration must match the flavor's target
environment. Register the required SHA-256 signing certificates for the app build that will be distributed. Do not
hardcode Firebase Console identifiers, project numbers or secrets in Android code.

App Check acquisition failures are recoverable and use the generic API error presentation:

```text
No pudimos verificar esta instalación. Revisá tu conexión e intentá nuevamente.
```

## Local Firebase Email Verification

For fictitious local-development accounts, Android can administratively verify
the currently signed-in Firebase user through the backend helper:

```http
POST /api/me/local-dev/email-verification
Authorization: Bearer <current Firebase ID token>
```

The backend endpoint must be available only under the backend
`local-firebase` profile, must be property-gated, and must require a
provisioned active backend user with `ROLE_USER`. Android calls it only after
normal backend provisioning or active-user loading succeeds. It does not send a
UID, email, request body, local-only header, or multipart flag.

After the helper returns `204 No Content`, Android reloads the Firebase user and
forces a new ID token. Profile-photo upload and profile activation still use the
normal backend endpoints and still rely on the real `email_verified` claim in
that refreshed token. Android does not update PostgreSQL, does not activate the
profile locally, and does not bypass legacy email-linking protections.

When auto-verification is enabled, local signup does not send a useless
Firebase verification email. The existing `Ya verifiqué` action remains the
manual fallback: in `local` it can call the helper and refresh the token; in
`dev` and `prod` it only reloads Firebase state and checks the real email-link
verification.

If the local helper, Firebase reload, or forced token refresh fails, Android
blocks local session bootstrap with a retryable safe message instead of
continuing to a confusing later photo-upload failure. Manual database profile
activation is no longer the recommended local workflow.

## Useful Commands

```bash
./gradlew :app:compileLocalDebugKotlin
./gradlew :app:testLocalDebugUnitTest
./gradlew :app:assembleLocalDebug
```

Use the `localDebug` variant for emulator or physical-device testing against a backend running on the host machine. See `local-android-networking.md` for the ADB reverse workflow.

## Backend Contract Docs

The Android repo includes backend-shared docs under `docs/commons/`. Refresh those docs from the backend project when API/domain contracts change, then update Android DTOs, mappers, error handling and UI behavior as needed.
