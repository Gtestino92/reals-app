## Frontend MVP Technical Debt

### 1. Make matchmaking search transition explicit and non-blocking

**Status:** Pending
**Priority:** High
**Area:** Frontend / Home / Matchmaking
**Type:** UX state modeling + flow correctness

#### Context

The current MVP Android app is being tested from commit:

```text
1b68a8775fd2e94481fbf24d079c038182288474
```

with only a local backend URL override, for example:

```properties
realsLocalBaseUrl=http://192.168.0.5:8080/
```

The newer unfinished `HomeCoordinator` refactor must not be used as the baseline for this task.

#### Problem

When the user taps **"Buscar chat"**, the app remains on the normal Home screen with buttons disabled and the text **"Preparando busqueda..."** for too long.

The UI only transitions to the **"Buscando chat"** screen after several asynchronous steps complete:

1. Device location is resolved.
2. The queue API call succeeds.
3. Home is fetched again.
4. The returned Home state contains `matchmaking.inQueue = true`.

This makes the app feel slow or frozen even when the backend is working correctly.

#### Goal

The frontend should model the matchmaking search lifecycle explicitly, instead of relying only on `homeLoading` and `screenModel.matchmaking.inQueue`.

The user should leave the normal Home UI immediately after tapping **"Buscar chat"**.

#### Suggested state model

Introduce an explicit frontend search phase, for example:

```kotlin
enum class MatchmakingSearchUiPhase {
    Idle,
    ResolvingLocation,
    JoiningQueue,
    Searching,
    Failed
}
```

Alternatively, use a sealed type if that fits better with the current UI state model.

#### Requirements

* Show a dedicated full-screen search/preparation screen immediately after the user taps **"Buscar chat"**.
* Do not keep the user visually stuck on Home with disabled buttons while location resolution and queue join are running.
* Display different copy depending on the current phase:

    * Resolving device location.
    * Joining matchmaking queue.
    * Searching for a compatible chat.
* Once `enqueueMatchmaking` succeeds, keep the user on the searching screen even before the next `/api/me/home` response confirms `inQueue = true`.
* If enqueue fails, return to Home and show the backend/domain error clearly.
* If location permission or location resolution fails, show a recoverable state and preserve the existing manual fallback path.
* Preserve current backend contracts and endpoint calls.
* Preserve current MVP behavior: after a match is found, Home polling/routing should still open the first chat automatically.
* Avoid a broad `RealsRootViewModel` refactor.
* Do not introduce Navigation Compose as part of this task.
* Do not depend on the unfinished `HomeCoordinator` refactor.

#### Acceptance criteria

* Tapping **"Buscar chat"** immediately leaves the normal Home content.
* The user sees a dedicated search/preparation screen during location resolution and queue join.
* The screen clearly explains what the app is doing.
* Home buttons are no longer visually presented as frozen during queue transition.
* Queue join errors are recoverable and visible.
* Manual location fallback still works in local/dev testing.
* Cancel search is available once the user is actually in queue.
* The app still transitions automatically to first chat when a match is created.
* The task builds successfully with one of:

```bash
./gradlew :app:assembleLocalDebug
```

or:

```bash
./gradlew :app:compileLocalDebugKotlin
```

---

### 2. Replace fragile custom matchmaking loading animation

**Status:** Pending
**Priority:** High
**Area:** Frontend / Matchmaking / Loading UI
**Type:** UX polish + accessibility

#### Context

The current **"Buscando chat"** screen uses a custom `rememberInfiniteTransition` pulse animation that scales a circle.

On a physical Android device, this animation may appear static, too subtle, or broken.

#### Problem

The loading state does not reliably communicate ongoing activity.

The user may see the **"Buscando chat"** screen as frozen, even though the app is polling Home and waiting for a match.

The current animation is decorative and too easy to miss.

#### Goal

Replace the fragile custom pulse with a clearer and more robust loading UI for the matchmaking search flow.

The screen should communicate activity even if custom animations are subtle, slow, disabled, or not visually obvious on the physical device.

#### Requirements

* Replace or supplement the custom pulse with a standard Material loading indicator, such as:

```kotlin
CircularProgressIndicator()
```

* Add a simple fallback text animation, for example:

    * `Buscando chat`
    * `Buscando chat.`
    * `Buscando chat..`
    * `Buscando chat...`

* The fallback text animation should use simple Compose state and `LaunchedEffect` with `delay`, rather than relying only on `rememberInfiniteTransition`.

* The screen should still be understandable if animations are disabled or reduced at the Android system level.

* Support the matchmaking search phases introduced by the search lifecycle task, if available:

    * Resolving location.
    * Joining queue.
    * Searching for compatible chat.
    * Waiting for match.

* Keep the cancel action clear and available only when it is valid.

* Do not introduce new libraries.

* Do not change backend polling behavior unless required by the explicit search state model.

* Apply basic system inset safety if touching this screen layout, but do not perform a broad app-wide insets refactor in this task.

#### Acceptance criteria

* The searching screen visibly communicates activity on a physical Android device.
* The loading state is understandable even if Android animations are disabled or reduced.
* The screen no longer appears frozen while polling for a match.
* The loading copy matches the actual search phase.
* The cancel search action remains available and correctly enabled/disabled based on actual state.
* Existing Home polling and automatic transition to first chat still work.
* The task builds successfully with one of:

```bash
./gradlew :app:assembleLocalDebug
```

or:

```bash
./gradlew :app:compileLocalDebugKotlin
```


---

### 3. Fix Android keyboard and responsive layout behavior

**Status:** Pending
**Priority:** High
**Area:** Frontend / Compose UI / Forms / Chat
**Type:** Responsive layout + keyboard handling + device UX

#### Context

The current MVP Android app is being tested from commit:

```text id="m2xpmd"
1b68a8775fd2e94481fbf24d079c038182288474
```

with only a local backend URL override, for example:

```properties id="rkyzxe"
realsLocalBaseUrl=http://192.168.0.5:8080/
```

The app runs on a physical Android device. `MainActivity` uses `enableEdgeToEdge()`, and the manifest uses `android:windowSoftInputMode="adjustResize"`.

The newer unfinished `HomeCoordinator` refactor must not be used as the baseline for this task.

#### Problem

Some screens do not behave correctly when the Android soft keyboard appears.

Known or likely affected areas:

* Login screen.
* Profile creation/editing forms.
* Manual location fallback in Home.
* Chat composer.
* Visual approval personal message.
* Scheduling screens if any input is present or added later.

The current layouts often rely on combinations of:

```kotlin
Modifier.fillMaxSize().padding(...)
```

or:

```kotlin
Modifier.fillMaxSize().verticalScroll(...).padding(...)
```

without consistently applying `imePadding()`, `safeDrawingPadding()`, or a reusable screen container.

This can cause inputs or buttons to be covered by the keyboard, content to become difficult to scroll, or bottom actions to feel cramped on physical devices.

#### Goal

Audit and fix keyboard/responsive behavior across the MVP frontend screens without doing a broad UI redesign.

The app should remain usable on physical Android devices when the keyboard is open.

#### Requirements

* Audit all user-input screens and identify which ones need keyboard/inset handling.
* Apply appropriate Compose modifiers such as:

    * `safeDrawingPadding()`
    * `imePadding()`
    * `navigationBarsPadding()`, if needed
    * `verticalScroll(...)` where forms need to remain scrollable
* Avoid blindly stacking paddings in a way that creates excessive whitespace.
* Prefer a small reusable screen/container helper if it reduces duplication, but do not introduce a large UI architecture refactor.
* Keep the current visual style and screen structure unless a layout is objectively broken.
* Preserve current business logic, API calls, and navigation behavior.
* Do not introduce Navigation Compose as part of this task.
* Do not depend on the unfinished `HomeCoordinator` refactor.
* Do not change backend contracts.

#### Screens to review

At minimum, review and fix:

1. **Login screen**

    * Email/password fields must stay visible while typing.
    * Primary actions must remain reachable with the keyboard open.
    * The screen should scroll if vertical space is constrained.

2. **Create/edit profile screen**

    * Text fields near the bottom must not be hidden by the keyboard.
    * Multi-field rows should remain usable on narrow phones.
    * Consider switching tightly packed horizontal rows to vertical layout on small widths if needed.

3. **Home manual location fallback**

    * Latitude, longitude, and accuracy fields must remain visible and editable with the keyboard open.
    * Submit action must remain reachable.
    * The fallback should not make the whole Home feel broken on small devices.

4. **Chat screen**

    * The message composer must stay above the keyboard.
    * The message list should resize correctly when the keyboard opens.
    * The user should still be able to see recent messages while typing.
    * Avoid the keyboard covering send/decision buttons.

5. **Visual approval screen**

    * The personal message input must stay visible while typing.
    * Approve/reject actions should remain reachable after keyboard dismissal or through scroll.

6. **Scheduling screen**

    * Ensure content does not overlap with system bars.
    * If any text input exists or is added later, it must behave correctly with the keyboard.

#### Suggested implementation approach

* Start with the most visible issue: Chat composer + Login.
* Then apply the same pattern to other form-heavy screens.
* Consider a small helper such as:

```kotlin
@Composable
fun RealsScreenContainer(
    modifier: Modifier = Modifier,
    keyboardAware: Boolean = false,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
)
```

Only add this helper if it simplifies the changes without forcing a broad migration.

A simple screen modifier pattern may be enough:

```kotlin
Modifier
    .fillMaxSize()
    .safeDrawingPadding()
    .imePadding()
    .padding(24.dp)
```

For form screens:

```kotlin
Modifier
    .fillMaxSize()
    .safeDrawingPadding()
    .imePadding()
    .verticalScroll(rememberScrollState())
    .padding(24.dp)
```

For Chat, avoid making the entire chat screen scrollable. The preferred structure is:

* Header at top.
* Message list with `Modifier.weight(1f)`.
* Composer at bottom.
* Root container applies keyboard/inset handling.

#### Acceptance criteria

* On a physical Android device, the Login screen remains usable with the keyboard open.
* Chat composer remains visible above the keyboard.
* Chat message list resizes instead of being covered by the keyboard.
* Profile forms can be completed without fields/buttons being hidden by the keyboard.
* Manual location fallback can be used with the keyboard open.
* Visual approval personal message input remains usable.
* No screen content is hidden behind system navigation/status bars.
* No broad UI/navigation refactor is introduced.
* Existing MVP flow still works:

    * Login.
    * Profile.
    * Photos.
    * Profile activation.
    * Home.
    * Buscar chat.
    * First chat.
* The task builds successfully with one of:

```bash
./gradlew :app:assembleLocalDebug
```

or:

```bash
./gradlew :app:compileLocalDebugKotlin
```

#### Manual test checklist

Run these checks on a physical Android device:

* Open Login, focus email/password, verify fields and buttons remain reachable.
* Open profile create/edit, focus fields near the bottom, verify scrolling works.
* Open Home manual location fallback, edit latitude/longitude/accuracy, verify submit remains reachable.
* Enter a chat, open keyboard, type a multi-line message, verify composer and send button remain visible.
* In chat, receive or send messages while keyboard is open, verify the message list remains usable.
* Open visual approval, focus personal message input, verify keyboard does not cover the input permanently.
* Rotate or test on a smaller screen width if available.


# Frontend technical debt - follow-up items

## 1. Review recently implemented matchmaking search UI changes

### Status

Pending review.

### Context

Recent manual changes improved the transition from Home to the matchmaking search screen:

* The app now leaves the idle Home screen immediately after the user taps "Buscar chat".
* The same `SearchingChatScreen` is reused for the pre-queue and in-queue phases.
* The screen now shows stable text from the beginning:

    * Title: `Buscando chat`
    * Body: `Estamos buscando alguien compatible. Cuando encontremos una persona, vas a entrar al chat automaticamente.`
* The cancel button is rendered consistently, but disabled until the backend confirms the user is actually in queue.
* The earlier visual jump between "Preparando búsqueda" and "Buscando chat" was reduced by using fixed-height text slots.
* A local `joiningQueue` state was added to avoid flashing back to the idle Home screen between location resolution and queue confirmation.

### Problem

These changes were made manually and incrementally. They work better visually, but should be reviewed and consolidated to avoid fragile local UI state.

Potential issues to review:

* `locating` and `joiningQueue` may overlap or fail to reset on all error paths.
* `joiningQueue` should reset reliably if `enqueueMatchmaking` fails.
* The search screen should not get stuck if the backend rejects enqueueing.
* The cancel button should only be enabled when the backend confirms the queue state.
* There should be no visual flash back to Home between:

    * resolving location,
    * joining queue,
    * confirmed searching state.
* `SearchingChatScreen` should not duplicate static strings across call sites.
* The screen should remain stable when title/body/button state changes.
* The local implementation should be checked against the actual current branch, especially if later refactors introduce `HomeCoordinator` or similar orchestration.

### Desired outcome

Create a cleaner, explicit search transition model for the Home/matchmaking flow.

Possible states:

```kotlin
Idle
ResolvingLocation
JoiningQueue
Searching
Failed
```

The UI should derive from one explicit state instead of combining several booleans ad hoc.

### Acceptance criteria

* Tapping "Buscar chat" immediately navigates away from idle Home.
* No temporary flash back to idle Home occurs before the search screen appears.
* The same title/body is shown throughout the pre-search/search transition.
* The cancel button is visible throughout, disabled before queue confirmation and enabled after queue confirmation.
* Failed location or enqueue attempts return the user to a safe state with a visible error.
* No duplicated search title/body strings remain.
* The implementation is compatible with the branch currently used for MVP testing.

## 2. Review custom searching indicator behavior

### Status

Pending review.

### Context

The original `rememberInfiniteTransition` pulse animation did not visibly animate on a physical Android device. A later alternative using changing dots was discussed, with a preference for state-based discrete animation rather than interpolation-based animation.

### Problem

The current loading/searching indicator may still be too fragile or visually inconsistent across emulator and physical devices.

Potential causes to review:

* Animation too subtle.
* `rememberInfiniteTransition` not producing visible changes in this specific layout.
* Low contrast in dark/light theme.
* Dot size/alpha not changing enough.
* Layout constraints clipping or hiding animation.
* Animation state being recreated unexpectedly.

### Desired outcome

Use a simple, robust, visible searching indicator that works consistently on emulator and real Android devices.

Preferred approach:

* Avoid relying on subtle alpha/scale interpolation.
* Use an explicit state update, for example an active dot index updated with `LaunchedEffect`.
* Keep the animation non-distracting.
* Do not add new dependencies.

### Acceptance criteria

* The indicator visibly changes on a physical Android phone.
* The searching screen remains visually stable.
* The title and body do not shift while the indicator animates.
* The implementation is small and easy to maintain.
