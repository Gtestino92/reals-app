---
apply: always
---

# Always

- Answer in the user's language.
- Treat `AGENTS.md` and `docs/` as the source of truth for project context.
- Preserve backend-owned lifecycle and state-machine behavior.
- Prefer existing Kotlin, Compose, coordinator and repository patterns over new abstractions.
- Keep changes scoped to the requested Android/frontend task.
- Do not introduce behavior listed as deferred in `docs/technical-debt-frontend-mvp.md` or `docs/technical-debt-frontend-prod.md` unless explicitly requested.
- Do not change backend API paths from Android.
- Do not introduce Navigation Compose unless explicitly requested.
- Work branches created or renamed by agents should use an appropriate prefix such as `feature/`, `fix/`, `refactor/`, `chore/`, `docs/`, or `test/`.
- Work branches must not track `origin/development`, `origin/main`, or `origin/master`; create them with `git switch --no-track -c <prefix>/<task-name> origin/development` and unset accidental protected-base upstreams before any push.
