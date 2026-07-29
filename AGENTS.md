# Repository Instructions for Coding Agents

## Source of Truth and Branch Discipline
- Treat current GitHub code and documentation as the source of truth.
- Confirm the current branch and HEAD before recommendations or edits.
- Use `development` as the normal comparison base unless the task explicitly names another base.
- Inspect actual files and relevant tests before proposing code.
- Compare the feature branch against the base before changing it.
- Do not reconstruct architecture solely from task descriptions, prior reports, or memory.

## Scope and Architecture Discipline
- Prefer small, mechanical, controlled changes.
- Avoid broad architecture, navigation, contract, persistence, or visual redesigns unless required.
- Do not introduce Navigation Compose without an explicit architectural decision.
- Avoid changing global navigation or `HomeCoordinator` unless the task genuinely requires it.
- Preserve existing backend contracts unless the task explicitly changes them.
- Do not perform opportunistic unrelated refactors.

## Mandatory Invariant and Mutation Audit
Before changing stateful, concurrent, lifecycle, timing, caching, or derived-data code, identify the affected invariants and every production path that constructs, copies, replaces, persists, or derives behavior from the affected state.

Required workflow:
1. State the affected invariants.
2. Find every assignment, `copy`, mapper, constructor, repository update, and API-response installation involving the state.
3. Build a compact mutation matrix.
4. Verify every row after implementation.
5. Treat coupled fields as one atomic conceptual value.
6. Never rely on eventual polling or refresh to repair a temporary inconsistent state unless that is an explicit product rule.

Examples of coupled state:
- value plus local receipt timestamp;
- entity plus denormalized latest-item fields;
- status plus terminal timestamps/reason;
- optimistic item plus delivery state;
- backend page/cursor plus accumulated list.

## Adversarial Review Checklist
Explicitly review for:
- stale UI state;
- overlapping requests;
- out-of-order responses;
- local time advancing between responses;
- backend and local lifecycle transitions racing;
- retries and idempotency;
- null, missing, malformed, or legacy fields;
- one-shot events being consumed before completion;
- action responses returning fresher state than polling;
- state updates occurring through secondary paths rather than the main load path.

## Text Encoding And User-Visible Strings
- Treat every repository text file as UTF-8.
- Preserve valid Unicode characters directly, including Spanish accents, `ñ`, `ü`, `¿`, and `¡`.
- Never introduce mojibake such as `Ã¡`, `Ã©`, `Ã­`, `Ã³`, `Ãº`, `Ã±`, `Â¿`, `Â¡`, malformed smart quotes, or the Unicode replacement character `�`.
- When editing files on Windows, use tools and APIs that read and write UTF-8 explicitly. Do not use shell redirection or file-writing commands whose encoding depends on the platform default.
- Do not encode valid Spanish text as Latin-1, Windows-1252, escaped byte sequences, or already-corrupted UTF-8 text.
- Do not convert an entire file's encoding, line endings, formatting, or unrelated contents merely to edit one string.
- Before completion, inspect every newly added or modified user-visible string in the final diff and confirm that accented characters appear correctly in the source file.
- Search added lines for common mojibake markers using an equivalent command to `git diff --unified=0 | rg "^\+.*(Ã|Â|â€|�)"`.
- Inspect and correct every match unless the malformed text is intentionally present in an encoding-specific test fixture or documentation example.
- Do not perform global replacement of suspected mojibake without determining the intended original text.

## Testing Rules
- Tests must cover materially different mutation paths, not only the main acceptance scenario.
- Add focused regression coverage for every fixed defect.
- Prefer pure unit tests and focused module/flavor tests.
- Run the smallest relevant Gradle task or exact test class.
- Do not run the complete test suite or every Gradle task.
- If a full suite appears necessary, stop and explain why instead of running it.
- Report exact commands and results.

## Android Gradle Validation
- Use the Gradle Wrapper from the existing checkout.
- The repository default is a 3 GiB Gradle heap with `org.gradle.workers.max=2`.
- Run only one Gradle invocation at a time.
- Do not use `clean`, `--no-daemon`, `--no-configuration-cache`, or `--offline` for routine validation.
- Do not run `gradlew --stop` or kill Java processes as a routine first response.
- Use focused flavor/task validation rather than every variant or the complete test suite.
- Normal focused compile:
  `.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:compileLocalDebugKotlin`
- If the Kotlin compiler daemon is demonstrably stalled in the current Windows environment, retry once with:
  `.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:compileLocalDebugKotlin`
- Treat `in-process` as an environment fallback, not a project-wide default.
- Reuse one focused unit-test invocation with multiple `--tests` filters where practical.
- Never start another Gradle command while an earlier one remains active.
- Before terminating a stuck build, record the active process command line and inspect the Gradle daemon log; terminate only processes proven to belong to the current build.

## Documentation Rules
- Update canonical product documentation only when behavior or contract changes.
- Do not add implementation-only details to shared API/domain documentation.
- Keep agent workflow instructions in `AGENTS.md`.
- Avoid duplicating the same rule in several files.

## Write and Delivery Restrictions
- Do not commit, push, merge, open or close PRs, or modify deployment resources unless explicitly requested.
- Do not modify backend or AWS resources from Android tasks.
- Before final delivery, inspect added and modified text for malformed Unicode or mojibake.
- Final reports must contain root cause, invariants, mutation matrix summary, files changed, tests and commands, remaining risks, `git diff --stat`, and a concise final diff summary.
