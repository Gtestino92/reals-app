---
apply: always
---

# Android

- Compose screens should stay mostly presentational and call callbacks for actions.
- `RealsRootViewModel` should orchestrate; feature-specific rules belong in coordinators/handlers.
- Repositories should remain API/data adapters.
- DTOs live under `app/src/main/java/com/reals/app/data/dto`.
- Domain models live under `app/src/main/java/com/reals/app/domain/model`.
- Mappers live under `app/src/main/java/com/reals/app/data/mapper`.
- Use Retrofit paths that match `docs/commons/api.md` and `docs/commons/openapi.yaml`.
- Frontend timers are advisory UX only. Backend remains the source of truth.
- Notification taps should refresh or return to Home unless a task explicitly defines a safe deep-link flow.
- Render user/backend text as plain Compose `Text`; do not add WebView or HTML/Markdown rendering without a security plan.
