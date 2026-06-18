TODO test gaps for the next pass:

- `app/src/main/java/com/reals/app/data/repository/ProfileRepository.kt`: `addMyProfilePhotoFile` and
  `replaceMyProfilePhotoFile` are not covered by JVM unit tests because they depend on Android `Context`, `Uri`,
  `ContentResolver`, and multipart file reading. A small production refactor could extract a `ProfilePhotoFileReader`
  interface and keep the Android implementation behind it.
- `app/src/main/java/com/reals/app/data/repository/FirebaseAuthRepository.kt`: not covered in this pass because it
  calls Firebase SDK APIs directly. Cover with a Firebase wrapper interface or an integration/instrumented layer later.
- Compose UI rendering remains intentionally uncovered in local JVM tests. Cover with instrumented Compose tests in a
  separate pass when emulator-based validation is acceptable.
