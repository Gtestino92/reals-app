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

## Testing Rules
- Tests must cover materially different mutation paths, not only the main acceptance scenario.
- Add focused regression coverage for every fixed defect.
- Prefer pure unit tests and focused module/flavor tests.
- Run the smallest relevant Gradle task or exact test class.
- Do not run the complete test suite or every Gradle task.
- If a full suite appears necessary, stop and explain why instead of running it.
- Report exact commands and results.

## Documentation Rules
- Update canonical product documentation only when behavior or contract changes.
- Do not add implementation-only details to shared API/domain documentation.
- Keep agent workflow instructions in `AGENTS.md`.
- Avoid duplicating the same rule in several files.

## Write and Delivery Restrictions
- Do not commit, push, merge, open or close PRs, or modify deployment resources unless explicitly requested.
- Do not modify backend or AWS resources from Android tasks.
- Final reports must contain root cause, invariants, mutation matrix summary, files changed, tests and commands, remaining risks, `git diff --stat`, and a concise final diff summary.
