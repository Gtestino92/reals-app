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

## 3. Split chat actions from the message composer

### Status

Pending.

### Context

The current chat composer combines two different responsibilities:

1. Message writing:

    * Text input.
    * Send button.

2. Chat-level actions:

    * Approve chat.
    * More actions.
    * Exit flow actions.
    * Timed exit request actions.

When the keyboard opens, the whole composer moves with it. This makes "Aprobar chat" and "Mas acciones" move together with the message input, which feels wrong.

### Problem

Only the message input and send button should behave as part of the keyboard-adjacent composer. Chat-level actions should not move together with the keyboard as if they were part of text entry.

### Desired outcome

Split the current composer into two UI components:

```kotlin
ChatActionsPanel
MessageComposer
```

Suggested layout:

```text
Header / chat status
ChatActionsPanel
MessageList
MessageComposer
Keyboard
```

Where:

* `ChatActionsPanel` contains approve/exit/more-actions controls.
* `MessageComposer` contains only:

    * `OutlinedTextField`
    * `Enviar`

### Acceptance criteria

* Opening the keyboard moves only the message input area.
* "Aprobar chat" and "Mas acciones" are no longer attached to the keyboard.
* Chat-level actions remain available in a stable, predictable location.
* Message sending behavior remains unchanged.
* Existing exit-request flows continue to work.

## 4. Refine chat keyboard layout to behave more like a messaging app

### Status

Pending.

### Context

A minimal `imePadding()` fix was applied so the keyboard no longer covers the message field. However, the current layout still behaves more like a resized form than a native messaging interface.

### Problem

When the keyboard appears, the message list visibly shrinks. In many chat apps, the composer behaves as a bottom overlay connected to the keyboard, and the message list scrolls behind/above it instead of feeling like the whole screen was compressed.

### Desired outcome

Rework the chat screen layout around a `Box`-based structure:

```text
Box full screen
├─ Column: header/status + message list
└─ Bottom-aligned message composer with imePadding()
```

The message list should account for the composer height using bottom padding, rather than being structurally compressed by the keyboard in a way that feels abrupt.

### Acceptance criteria

* Keyboard opening feels natural for a chat screen.
* The composer stays visually attached to the keyboard.
* The message list remains scrollable and does not feel abruptly squeezed.
* Latest messages remain reachable when the keyboard is open.
* The layout works on emulator and physical Android devices.
* The solution does not break edge-to-edge/safe-area behavior.

## 5. Review keyboard focus behavior after sending a message

### Status

Review pending.

### Context

A manual adjustment was made because the keyboard disappeared after sending a message. The likely cause was that the `OutlinedTextField` became disabled while `sending = true`, causing it to lose focus and close the keyboard.

The local fix was to avoid disabling the text field during message sending, while still disabling the Send button.

### Problem

This should be reviewed to ensure the behavior is correct across all chat states.

Important distinction:

* During normal message sending, the text field should remain enabled and focused.
* The Send button can be disabled to prevent duplicate sends.
* The text field should only become disabled when the chat is not writable or a blocking chat-level action requires it.

### Desired outcome

Keep keyboard focus stable after sending messages.

### Acceptance criteria

* Sending a message does not close the keyboard.
* The text field remains focused after tapping Send.
* The Send button is disabled while a send is in progress.
* Chat-level actions do not accidentally disable the input unless necessary.
* The behavior remains correct when the chat becomes read-only, closed, expired, or otherwise unavailable.

## 6. Review chat busy-state granularity

### Status

Pending.

### Context

The current chat screen appears to use broad `busy` state for several unrelated operations:

* Sending a message.
* Approving a chat.
* Exit flow actions.
* Possibly refreshing or loading.

### Problem

Using one broad `busy` flag can cause unrelated UI elements to lock together. For example:

* Sending a message should not necessarily disable the message text field.
* Approving a chat may need to disable chat-level action buttons, but not necessarily all composer UI.
* Refreshing messages should not block typing.

### Desired outcome

Split busy/loading state by responsibility.

Suggested state categories:

```kotlin
sendingMessage
loadingChatAction
refreshingMessages
blockingChatTransition
```

### Acceptance criteria

* Sending only disables the Send button or prevents duplicate send.
* Chat actions only disable related chat action buttons.
* Silent refresh does not block typing.
* Keyboard focus is not lost during send.
* UI state remains readable and predictable.

## 7. Improve sent-message feedback timing

### Status

Pending.

### Context

Outgoing messages currently appear only after the backend send/refresh flow completes. This was identified as a UX gap and should be solved later with optimistic outgoing messages.

### Problem

While sending:

* Buttons can be disabled.
* The message is not visible yet.
* The user sees no immediate confirmation that the message was inserted into the conversation.

### Desired outcome

This should be handled together with optimistic outgoing messages, but the surrounding UI behavior should also be reviewed:

* The draft should clear immediately or at a predictable time.
* The sent message should appear immediately in the message list.
* The Send button should show a sending state without blocking the rest of the chat unnecessarily.
* Failed sends should be visible and recoverable.

### Acceptance criteria

* Tapping Send gives immediate visual feedback.
* The message appears in the conversation without waiting for polling.
* Failed sends do not silently disappear.
* Duplicate bubbles are avoided when the backend-confirmed message arrives.

## 8. Review emulator and physical-device location behavior for local testing

### Status

Pending.

### Context

The emulator location can differ significantly from the physical Android device location. This affects matchmaking distance tests.

For Buenos Aires testing, the emulator can be configured with:

```text
Latitude:  -34.6037
Longitude: -58.3816
```

### Problem

Manual emulator location setup is easy to forget, which can lead to confusing matchmaking results.

### Desired outcome

Document and/or improve the local testing flow for location-sensitive matchmaking.

Possible improvements:

* Add a local testing note in frontend docs.
* Provide an optional debug-only manual location override.
* Clearly indicate the location being used before enqueueing matchmaking.
* Keep the production flow based on real device location.

### Acceptance criteria

* Local testers can reliably simulate Buenos Aires location in emulator.
* Distance-based matchmaking tests are reproducible.
* The app does not silently enqueue users with unexpected far-away coordinates during local testing.

## 9. Review message list autoscroll behavior

### Status

Pending.

### Context

When outgoing or incoming messages are added, the chat should generally keep the latest messages visible, especially if the user is already near the bottom.

### Problem

As the chat evolves, message insertion, keyboard open/close, and polling can interact poorly with scroll position.

### Desired outcome

Define and implement predictable autoscroll behavior:

* If the user is near the bottom, new messages scroll into view.
* If the user manually scrolled up, do not force-scroll unexpectedly.
* Sending a message should bring the sent message into view.
* Keyboard opening should not hide the latest message behind the composer.

### Acceptance criteria

* Latest own message is visible after sending.
* Incoming messages are visible if the user is already at the bottom.
* User scroll position is respected when reviewing older messages.
* Behavior is stable with keyboard open and closed.



