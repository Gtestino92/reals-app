# Frontend Security Notes

## XSS And User Text

This app is native Android with Jetpack Compose. It does not render backend or user-provided content through `WebView`, `Html.fromHtml`, Markdown, or injected JavaScript. User text is rendered as plain Compose `Text`.

The frontend still normalizes text before sending it to the backend:

- Profile fields strip unsafe invisible control characters.
- Single-line fields collapse whitespace and remove line breaks.
- Bio, chat messages, and safety report details preserve normal line breaks.
- HTML-like tags are rejected in user-editable text.
- User-editable text has explicit frontend length limits.

This is defense in depth only. The backend must continue to validate, store, and encode user content safely for every consumer, especially if a future web frontend or admin dashboard renders the same data.

## Current XSS-Sensitive Surfaces

- No `WebView`.
- No HTML rendering APIs.
- No Markdown/rich-text rendering.
- Image URLs from the backend are loaded as images only, not executed as script.

If any future feature adds `WebView`, rich text, Markdown, or external link opening, it must add an allowlist and sanitization strategy before rendering remote/user content.

## Validation Ownership

Frontend validation is for immediate UX and risk reduction. Backend validation remains authoritative and must not rely on frontend checks.
