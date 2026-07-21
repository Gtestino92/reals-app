# Local Android Networking

Use ADB reverse for Android local development. This avoids depending on router
IP addresses, Wi-Fi isolation rules, or Windows Firewall inbound rules.

## Model

```text
Android localDebug
    |
    | http://127.0.0.1:8080
    v
ADB reverse
    |
    v
Windows localhost:8080
    |
    v
Docker Reals backend
```

Profile-photo URLs use the same mechanism:

```text
Android
    |
    | http://127.0.0.1:9000
    v
ADB reverse
    |
    v
Windows localhost:9000
    |
    v
Docker MinIO
```

## Prerequisites

- Docker Desktop and the local backend are available.
- Android SDK Platform Tools are installed.
- `adb` is available in `PATH`.
- An emulator is running or a physical device is connected.
- USB debugging or wireless debugging is enabled for physical devices.

## Standard Startup Workflow

```powershell
# Backend repository
docker compose up -d

# Android repository
.\tools\setup-android-local.ps1
```

Then select and run `localDebug` from Android Studio.

`localDebug` installs as `com.reals.app.local` with the visible name `Reals Local`. This is a distinct app from
`dev` and `prod`, so it has separate app data, Firebase Auth state, FCM registration, and App Check debug-token
lifecycle. Register the debug token printed by this new local app under the Firebase Android App for
`com.reals.app.local`.

## Verify ADB Mappings

For one connected device:

```powershell
adb reverse --list
```

For multiple connected devices:

```powershell
adb devices
adb -s <serial> reverse --list
```

Configure all usable connected devices:

```powershell
.\tools\setup-android-local.ps1
```

Configure one specific device:

```powershell
.\tools\setup-android-local.ps1 -Serial emulator-5554
```

## Verify Backend

From Windows:

```powershell
curl.exe http://localhost:8080/api/ping
```

From the Android emulator or device browser, when ADB reverse is active:

```text
http://127.0.0.1:8080/api/ping
```

## Build and Install Manually

```powershell
.\gradlew :app:installLocalDebug
```

No `realsLocalBaseUrl` override is needed for the normal local workflow because
`http://127.0.0.1:8080/` is the local default.

`dev` and `prod` must not depend on ADB reverse. They require HTTPS backend URLs and cleartext remains prohibited.

## Backend MinIO Requirement

The backend repository must expose MinIO on host port `9000` and produce
Android-readable presigned URLs with:

```text
S3_PRESIGNED_URL_ENDPOINT=http://127.0.0.1:9000
```

The backend's internal S3 endpoint remains:

```text
S3_ENDPOINT=http://minio:9000
```

Distinction:

```text
S3_ENDPOINT
-> backend container accesses MinIO inside Docker

S3_PRESIGNED_URL_ENDPOINT
-> Android accesses MinIO through ADB reverse
```

Do not change backend files from this Android repository. Apply or verify the
backend Compose/environment value in the backend repository separately.

Android does not rewrite backend-provided presigned photo URLs. The backend must emit Android-readable local URLs when
the local app is expected to load images through reverse port `9000`.

## Important Limitations

- The local APK reaches the PC only while the ADB reverse tunnel exists.
- Taking the phone away from the PC requires the future `devDebug` cloud environment.
- `127.0.0.1` without ADB reverse points to the Android device itself.
- The script does not open firewall ports.
- Changing Wi-Fi networks or router IPs no longer matters.
- Reinstalling the APK does not necessarily remove the reverse mapping.
- Restarting or disconnecting the emulator/device may remove the mapping.
- Re-run the script after restarting the emulator, rebooting the phone,
  restarting ADB, disconnecting USB, or switching between USB and wireless debugging.
