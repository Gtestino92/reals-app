TODO test gaps for the next pass:

- `app/src/main/java/com/reals/app/data/repository/ProfileRepository.kt`: `addMyProfilePhotoFile` and
  `replaceMyProfilePhotoFile` are not covered by JVM unit tests because they depend on Android `Context`, `Uri`,
  `ContentResolver`, and multipart file reading. A small production refactor could extract a `ProfilePhotoFileReader`
  interface and keep the Android implementation behind it.
- Direct Firebase SDK task execution inside `FirebaseAuthRepository` is not exercised by local JVM tests. Pure
  invalid-user/error-code classification and root terminal-session routing are covered; real Firebase task behavior
  still requires a Firebase integration or instrumented layer.
- Compose UI rendering remains intentionally uncovered in local JVM tests. Cover with instrumented Compose tests in a
  separate pass when emulator-based validation is acceptable.
