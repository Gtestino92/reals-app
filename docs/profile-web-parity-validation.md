# Android profile parity validation

## Scope and references

Work branch: `feature/profile-web-parity`, without an upstream. Android base and
HEAD before implementation: `87e297942308b8fa9bbad1efe8df8f0450df1300` from the
freshly fetched `origin/development`. Initial validation was delivered without commits or pushes; the user subsequently authorized publishing this branch. No PR or deployment was requested.
`gradle.properties` was not changed.

The sibling repositories were inspected read-only. Web's preview was initially
on `feature/my-profile-preview` and was merged into `main` (`131add3`) during this
task. The final inspected incoming Web location implementation is
`feature/public-profile-location@89a3dca34701ebf129e8b9960ca4e0deb91ddb3b`.
Backend location contract inspected:
`feature/public-profile-location@9b50d38e73786539eb52f9ac37039d440c08e0a0`.
Deployment of that contract was not verified.

Compared Web sources include `MyProfilePreview.tsx`, `PublicProfileView.tsx`,
location formatting, `ProfileQuestions.tsx`, its presentation helpers, hook,
contract/presentation/hook tests, `Onboarding.tsx`, `ProfileForm.tsx` and its tests.
Reviewed mobile overview and inline-edit screenshots under
`artifacts/profile-questions`. Backend DTO mapping and additive API/OpenAPI
documentation were also inspected. No sibling files were written by this task.

## Cause and resulting behavior

Android lacked the newer declared-location fields and owner preview. Its question
overview linked to a separate editor instead of exposing answered cards and
ordered public selection. Profile save buttons accepted unchanged data, and a
shared feedback reset erased messages belonging to other operations. Several
profile responses also installed captured state instead of preserving concurrent
updates.

The change adds nullable legacy-compatible location fields, a confirmed owner
projection using existing endpoints, inline question management, normalized dirty
comparisons, independent feedback and ownership checks on affected responses.
Review and Browse continue to use the existing shared visual card. No fake match,
new endpoint, dependency or navigation framework was introduced.

## Invariants and mutation audit

| State / paths inspected | Invariant and implementation check |
| --- | --- |
| Visual DTO construction, mapper, domain construction/copy, shared Review identity and Browse screen header | Only declared city/country are mapped. Missing fields default to null. Known country codes become Spanish names; unknown codes are omitted. No location is derived from search, GPS or distance. |
| Preview opening, reopening, retry and resume | Profile, photos, catalog and answers are awaited before one projection is installed. Request generation and exact owner-session checks reject old results. Approved photos are sorted by saved position. Selected current semantic-version answers are sorted and limited to three. |
| Preview close, overlapping loads and session replacement | Closing invalidates and cancels the job. Replacement cancels obsolete work and removes incompatible state. Refresh may retain only a snapshot from the same session; a partial response is never published. |
| Question opening, refresh, resume and close | Catalog and answers are loaded together. Request and session checks protect installation; closing cancels reads. Failed reads require reconciliation before writes. |
| Question upsert, delete and selection results | The complete server answer list is authoritative. Current semantic versions govern eligibility. Selection replacement preserves order and supports zero selections. Independent selection drafts survive answer saves; deleted IDs are removed. |
| Inline drafts, Cancel, success, rejection and uncertainty | Drafts are per question and saveable. Cancel removes only that draft and never writes. Success retires only the submitted draft and returns focus. Rejected/uncertain writes retain the editor and text. Uncertain writes block further submissions until a successful read. A pending mutation remains observable across route changes. |
| Personal details and search preferences | Normalized meaningful changes control Save. Submission flags are installed synchronously. Responses install into the latest state, check session and returned profile identity, and preserve unrelated feedback and concurrent country data. |
| Photo read, reorder, add, replace, delete and activation feedback | Photo operations clear photo feedback, not personal/search confirmation. Affected photo responses check the initiating session and update the latest state. Busy guards serialize conflicting management operations. Activation clears its own feedback. Existing moderation, activation and resource-cleanup policies remain in place. |
| Profile forms, nested routes and restoration | Modal profile surfaces keep owner forms composed. Saveable forms and per-route list state preserve drafts/scroll. Saved profile routes are owner/profile scoped and refetch confirmed preview data after reconstruction. System Back uses existing callbacks/native dialogs. |

The review covered out-of-order results, overlapping requests, account/session
replacement, authoritative action responses, retries, missing/legacy location,
semantic-version mismatch, stale selections, deletion, navigation during writes,
and independent response-installation paths. There is no polling-based repair of
partially installed preview data. No owned-resource disposal effect was added.

## Parity matrix

`COMPLETE` describes implemented behavior with focused JVM coverage where
applicable. `PLATFORM-LIMITED` identifies Android behavior whose instrumentation
tests compile but could not run without a device. These labels do not claim live
backend or physical-device verification.

| Behavior compared with Web | Status | Evidence / platform difference |
| --- | --- | --- |
| Public city/country below identity in Review and counterpart Browse | COMPLETE | Shared formatter, DTO compatibility tests and Compose presentation tests. Live fields depend on backend deployment. |
| Spanish country name and graceful absent/unknown location | COMPLETE | JVM tests; no fallback to GPS/search/distance. |
| Owner preview entry at end; ACTIVE and DRAFT | COMPLETE | Existing owner reads, Browse card and discreet draft notice. |
| Confirmed details, approved photos, ordered public answers only | COMPLETE | Projection tests include semantic mismatch, stale answers, ordering and three-answer limit. |
| Atomic preview, retry, cancellation, reopening and identity replacement | COMPLETE | Focused handler tests include non-cooperative late completion and mismatched owner responses. |
| Answered overview and public selection with explicit save | COMPLETE | Inline cards, selected positions, ordering, removal and clear-all. |
| Separate unanswered/new-question screen | COMPLETE | Answered questions excluded; Compose coverage added. |
| Inline edit with only Save/Cancel and public indicator preserved | COMPLETE | Compose coverage added; no full-screen editor is used by the new UI. |
| Cancel, authoritative save, failure/uncertainty and deletion | COMPLETE | Handler/presentation JVM tests plus inline Compose tests. |
| Country before city, optional bio and consistent field ordering | COMPLETE | Creation and management forms updated. |
| Normalized meaningful changes; independent saving/confirmation feedback | COMPLETE | Dirty-state and handler regression tests. Saving labels track the relevant write, not unrelated busy operations. |
| Prominent question entry and photo expansion/collapse | COMPLETE | Question action before photos; existing Android section controls retained. |
| Focus, IME, system Back, long scrolling, recreation/process reconstruction | PLATFORM-LIMITED | Native full-screen dialogs replace Web history/DOM hiding; saveable route/form/list state and Compose tests added. Device execution remains outstanding. |
| Live deployed location contract and actual image/endpoint integration | PLATFORM-LIMITED | No deployed-backend or physical-device run was performed. |

Android-specific choices are native modal Back/focus handling, saveable Compose
state and IME insets rather than browser history/DOM focus APIs. Setup retains
Android's collapsible sections and activation flow; dedicated management forms
stay expanded. No unrelated Home, Scheduling, Second Chat or matchmaking behavior
was changed. Root edits are limited to profile surfaces, their restoration and
profile error/session handling.

## Validation

All Gradle commands used this checkout's wrapper, daemon/configuration/build-cache
flags, existing heap settings and one build at a time. No complete suite was run.

Phase 1 passed:

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.chat.PublicProfileLocationTest --tests com.reals.app.ui.chat.VisualProfileCardPresentationTest --tests com.reals.app.ui.chat.PartnerProfileScreenPresentationTest
```

Phase 2 passed, and the owner class was rerun after adding adversarial coverage:

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.root.OwnerProfilePreviewHandlerTest
```

Phase 3 passed (an earlier new regression fixture was corrected to distinguish
preserving another answer from the server deleting it):

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.profile.ProfileQuestionPresentationTest --tests com.reals.app.ui.root.ProfileQuestionOperationHandlerTest :app:compileLocalDebugAndroidTestKotlin
```

Phase 4 passed:

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.profile.ProfileDirtyStateTest --tests com.reals.app.ui.profile.ProfileCountryInputTest --tests com.reals.app.ui.profile.ProfileManagementPresentationPolicyTest --tests com.reals.app.ui.root.ProfileOperationHandlerCountriesTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoMutationTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoReorderTest --tests com.reals.app.ui.root.ProfileOperationHandlerLocalEmailVerificationTest :app:compileLocalDebugAndroidTestKotlin
```

Final joint regression command passed, with 98 JVM tests and Android-test Kotlin
compilation. The final owner-only rerun includes the additional owner-mismatch
test (seven owner tests, all passing).

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.chat.PublicProfileLocationTest --tests com.reals.app.ui.chat.VisualProfileCardPresentationTest --tests com.reals.app.ui.chat.PartnerProfileScreenPresentationTest --tests com.reals.app.ui.root.OwnerProfilePreviewHandlerTest --tests com.reals.app.ui.profile.ProfileQuestionPresentationTest --tests com.reals.app.ui.root.ProfileQuestionOperationHandlerTest --tests com.reals.app.ui.profile.ProfileDirtyStateTest --tests com.reals.app.ui.profile.ProfileCountryInputTest --tests com.reals.app.ui.profile.ProfileManagementPresentationPolicyTest --tests com.reals.app.ui.root.ProfileOperationHandlerCountriesTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoMutationTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoReorderTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoPrefetchTest --tests com.reals.app.ui.root.ProfileOperationHandlerLocalEmailVerificationTest :app:compileLocalDebugAndroidTestKotlin
```

`adb devices` returned no connected devices. Nine new Compose tests cover public
location, inline edit/cancel/success/uncertainty, unanswered filtering, normalized
details, preview return, save/restore and route reconstruction. They compiled;
they were not executed. OpenAPI YAML was parsed and its location properties
checked. `git diff --check`, UTF-8 decoding and added-line mojibake checks were
also performed.

Gradle repeatedly reported the existing configuration-cache serialization issue
in `:app:validateLocalEnvironment`; builds succeeded and discarded that cache
entry. No Gradle settings were changed to work around it.


Final focused follow-ups also passed: normalized dirty-state/country behavior
(11 JVM tests), then root profile routing (13 JVM tests) and Android-test Kotlin
compilation. These were targeted follow-ups to the last form/navigation edits:

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.profile.ProfileDirtyStateTest --tests com.reals.app.ui.profile.ProfileCountryInputTest :app:compileLocalDebugAndroidTestKotlin
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.ui.root.RealsRootViewModelProfileRoutingTest :app:compileLocalDebugAndroidTestKotlin
```

## Remaining verification

- Confirm deployed backend returns location fields under its existing access restrictions.
- Run the nine Compose tests and inspect long answers, font scaling, TalkBack,
  keyboard dismissal, system Back, scroll/focus return, configuration change,
  process recreation and foreground resume on Android hardware/emulator.
- Verify real signed photo URLs and account/session replacement against deployed services.

The final file inventory and tracked `git diff --stat` are recorded below.


## Final diff and file inventory

Tracked changes (`git diff --stat`; new files are listed separately):

```text
 .../java/com/reals/app/data/dto/VisualReviewDto.kt |   2 +
 .../com/reals/app/data/mapper/VisualMappers.kt     |   2 +
 .../com/reals/app/domain/model/VisualReview.kt     |   2 +
 .../com/reals/app/ui/chat/PartnerProfileScreen.kt  |   1 +
 .../com/reals/app/ui/chat/VisualProfileCard.kt     |   8 +
 .../reals/app/ui/profile/CreateProfileScreen.kt    |  46 +-
 .../app/ui/profile/MatchPreferencesSection.kt      |  22 +-
 .../reals/app/ui/profile/ProfileDetailsSection.kt  |  26 +-
 .../app/ui/profile/ProfileQuestionPresentation.kt  |  18 +-
 .../reals/app/ui/profile/ProfileQuestionScreen.kt  | 696 +++++----------------
 .../reals/app/ui/profile/ProfileStatusScreen.kt    |  42 +-
 .../reals/app/ui/root/ProfileOperationHandler.kt   | 143 +++--
 .../app/ui/root/ProfileQuestionOperationHandler.kt |  72 ++-
 .../main/java/com/reals/app/ui/root/RealsApp.kt    |  48 +-
 .../java/com/reals/app/ui/root/RealsRootUiState.kt |   3 +
 .../com/reals/app/ui/root/RealsRootViewModel.kt    |  24 +-
 .../test/java/com/reals/app/testutil/TestApi.kt    |   6 +-
 .../ui/profile/ProfileQuestionPresentationTest.kt  |  12 +
 .../root/ProfileOperationHandlerCountriesTest.kt   |  41 ++
 .../ui/root/ProfileQuestionOperationHandlerTest.kt |  29 +
 docs/commons/api.md                                |   1 +
 docs/commons/openapi.yaml                          |  12 +
 docs/commons/user-flow.md                          |  14 +
 23 files changed, 586 insertions(+), 684 deletions(-)
```

New files (not staged):

- `app/src/androidTest/java/com/reals/app/ui/profile/ProfileManagementParityTest.kt`
- `app/src/androidTest/java/com/reals/app/ui/profile/ProfileQuestionInlineTest.kt`
- `app/src/androidTest/java/com/reals/app/ui/profile/PublicProfilePresentationTest.kt`
- `app/src/main/java/com/reals/app/ui/chat/PublicProfileLocation.kt`
- `app/src/main/java/com/reals/app/ui/profile/OwnerProfilePreview.kt`
- `app/src/main/java/com/reals/app/ui/profile/OwnerProfilePreviewDialog.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileDirtyState.kt`
- `app/src/main/java/com/reals/app/ui/root/OwnerProfilePreviewHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileNavigationRestoration.kt`
- `app/src/test/java/com/reals/app/ui/chat/PublicProfileLocationTest.kt`
- `app/src/test/java/com/reals/app/ui/profile/ProfileDirtyStateTest.kt`
- `app/src/test/java/com/reals/app/ui/root/OwnerProfilePreviewHandlerTest.kt`
- `docs/profile-web-parity-validation.md`

Modified tracked files:

- `app/src/main/java/com/reals/app/data/dto/VisualReviewDto.kt`
- `app/src/main/java/com/reals/app/data/mapper/VisualMappers.kt`
- `app/src/main/java/com/reals/app/domain/model/VisualReview.kt`
- `app/src/main/java/com/reals/app/ui/chat/PartnerProfileScreen.kt`
- `app/src/main/java/com/reals/app/ui/chat/VisualProfileCard.kt`
- `app/src/main/java/com/reals/app/ui/profile/CreateProfileScreen.kt`
- `app/src/main/java/com/reals/app/ui/profile/MatchPreferencesSection.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileDetailsSection.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileQuestionPresentation.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileQuestionScreen.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileStatusScreen.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileOperationHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileQuestionOperationHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsApp.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsRootUiState.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsRootViewModel.kt`
- `app/src/test/java/com/reals/app/testutil/TestApi.kt`
- `app/src/test/java/com/reals/app/ui/profile/ProfileQuestionPresentationTest.kt`
- `app/src/test/java/com/reals/app/ui/root/ProfileOperationHandlerCountriesTest.kt`
- `app/src/test/java/com/reals/app/ui/root/ProfileQuestionOperationHandlerTest.kt`
- `docs/commons/api.md`
- `docs/commons/openapi.yaml`
- `docs/commons/user-flow.md`

Diff summary: additive location mapping/presentation; confirmed owner preview and scoped navigation restoration; inline questions and ordered explicit public selection; normalized form saving, independent feedback and guarded latest-state installation; focused tests and contract/product documentation.
