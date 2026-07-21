# Reals Android Documentation

This directory is the canonical documentation set for the Android frontend.

## Files

- `architecture.md`: Android structure, layers and feature ownership.
- `local-development.md`: local setup, flavors, Firebase and emulator notes.
- `local-android-networking.md`: ADB reverse workflow for local backend and MinIO access.
- `logging-and-logcat.md`: Reals logging policy and Android Studio/ADB Logcat filters.
- `testing.md`: automated test strategy and Gradle commands.
- `security.md`: frontend security and user-content rendering notes.
- App Check setup is covered in `architecture.md`, `local-development.md`, `infra.md`, `security.md`, and
  `commons/api.md`.
- `photo-management-decisions.md`: profile photo UX and implementation decisions.
- `infra.md`: Android/frontend infrastructure notes.
- `technical-debt-frontend-mvp.md`: MVP frontend decisions, shortcuts and deferred work.
- `technical-debt-frontend-prod.md`: production hardening and longer-term frontend work.

## Shared Backend Contract Docs

`docs/commons/` mirrors backend-owned contract and domain documentation:

- `commons/api.md`: current backend endpoint summary.
- `commons/openapi.yaml`: formal OpenAPI contract for client DTOs and API paths.
- `commons/domain.md`: backend entities, enums and invariants.
- `commons/state-machine.md`: backend state transitions.
- `commons/user-flow.md`: end-to-end product/backend flow.
- `commons/development.md`: shared development conventions.

`AGENTS.md` at the repository root is the primary instruction file for AI coding agents. `.aiassistant/rules/` contains JetBrains AI Assistant rules derived from the same source of truth.
