TODO test gaps for the next pass:

- `app/src/main/java/com/reals/app/data/repository/ProfileRepository.kt`: `addMyProfilePhotoFile` and
  `replaceMyProfilePhotoFile` are not covered by JVM unit tests because they depend on Android `Context`, `Uri`,
  `ContentResolver`, and multipart file reading. A small production refactor could extract a `ProfilePhotoFileReader`
  interface and keep the Android implementation behind it.
- Direct Firebase SDK task execution inside `FirebaseAuthRepository` is not exercised by local JVM tests. Pure
  invalid-user/error-code classification and root terminal-session routing are covered; real Firebase task behavior
  still requires a Firebase integration or instrumented layer.
- Compose UI rendering remains outside local JVM tests, but targeted instrumented Compose coverage now exists for
  profile photo crop/grid and selected secondary-flow responsiveness. Broad end-to-end screen rendering remains
  incomplete, and physical-device large-font validation is still required for accessibility-sensitive layouts.
