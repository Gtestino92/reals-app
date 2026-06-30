---
apply: always
---

# Review

Prioritize findings over summaries.

Look first for:

- Android taking ownership of backend lifecycle transitions;
- stale local screens that leave actions enabled after backend state changes;
- DTO/domain/mapper mismatches with the backend contract;
- Retrofit path or payload drift;
- missing handling for backend error codes;
- notification taps that deep-link into stale interactions;
- Compose screens that perform navigation or backend orchestration directly;
- sensitive payloads, tokens, user text or private media URLs in logs;
- missing tests or missing manual verification for changed lifecycle/contract behavior.

Report issues with file and line references when possible.
