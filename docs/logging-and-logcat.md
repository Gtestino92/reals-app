# Logging And Logcat

Android Logcat is device-wide. Messages from processes such as `artd`,
`system_server`, Google Play Services, `com.android.phone`,
`com.android.systemui`, `wpa_supplicant` and similar services are normal when no
application filter is active. Reals cannot stop those processes from writing to
the global Logcat buffers.

## Android Studio Filters

Select the intended emulator or smartphone in the Logcat device selector first.
Then use one of these queries.

Normal Reals development:

```text
package:mine level:DEBUG
```

Exact Reals package:

```text
package:com.reals.app level:DEBUG
```

Cleaner informational view:

```text
package:mine level:INFO
```

Warnings and errors only:

```text
package:mine level:WARN
```

Crashes only:

```text
package:mine is:crash
```

Crashes and stack traces:

```text
package:mine & (is:crash | is:stacktrace)
```

Recent Reals logs:

```text
package:mine age:5m
```

Use Logcat Compact view when desired and save or favorite the query in Android
Studio. Filtering changes only the displayed results; it does not change what
Android writes globally.

Do not commit `.idea/workspace.xml` or other user-specific Android Studio state
to persist a personal Logcat tab.

## Terminal Helper

Use the PowerShell helper to stream only the current Reals process:

```powershell
.\tools\logcat-reals.ps1
```

When more than one usable device is connected, choose one serial:

```powershell
adb devices
.\tools\logcat-reals.ps1 -Serial emulator-5554
```

Wireless debugging serials are also supported:

```powershell
.\tools\logcat-reals.ps1 -Serial 192.168.1.50:5555
```

The helper filters by the current process PID with `adb logcat --pid=<pid>`.
After the app process restarts, stop the script with `Ctrl+C` and run it again.
Android Studio's package-based query continues following the package across
process restarts.

The helper does not clear Logcat, restart the app, install the app, modify ADB
reverse mappings, change firewall rules or require Administrator privileges.

## Network Logging Policy

Reals API logging uses OkHttp's `HttpLoggingInterceptor`.

- `localDebug` and `devDebug`: `BASIC` request/response-line logging.
- `prodDebug`: `NONE`.
- All release variants: `NONE`.

The app does not log request or response bodies by default. Headers containing
credentials are redacted before any interceptor output.

OkHttp logging-interceptor `4.12.0` supports header redaction but does not expose
query-parameter redaction. Reals must not manually print complete presigned
photo URLs because S3-compatible signatures include temporary query parameters.
Profile photos are loaded by Coil, not by the Retrofit client.
