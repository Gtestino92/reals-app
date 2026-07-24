# Photo Management Decisions

## MVP

- Android profile photo management is slot-based with positions 1 through 9.
- Android supports only real file upload for profile photo mutations.
- Mock/URL photo actions were removed from Android production code.
- Backend multipart upload is the only supported Android profile photo mutation flow.
- The backend still requires `position` for multipart profile photo upload.
- Android hides `position` from the user by rendering a 3x3 grid and passing the selected slot internally.
- Adding a photo uses an empty grid slot. Replacing and deleting use the existing photo id.
- Reordering is intentionally not implemented in MVP.
- The MVP grid uses existing image URLs and Coil image loading. It does not generate real thumbnails.
- Adding, replacing or deleting a profile photo can return the profile to `DRAFT`. Android preserves existing
  backend Home interactions in that state and only prevents new matchmaking when Home reports `matchmaking.canSearch =
  false`.
- Photo mutations during visual review are allowed. Android does not block the mutation, snapshot visual-review photos,
  or cancel/restart the visual review after the mutation.

## Android Upload Preprocessing

- The backend remains authoritative for profile-photo security, validation, moderation and abuse prevention. Android preprocessing is defense in depth for the official client only; it must not be treated as preventing direct API abuse.
- Before multipart upload or file replacement, Android normalizes the selected/cropped image into a fresh temporary JPEG for privacy, memory, bandwidth and reliability.
- Android output is `image/jpeg`, uses maximum dimension `2048px`, JPEG quality `88`, normalized orientation and no intentionally copied client metadata such as GPS, EXIF, device model, timestamps, thumbnails, XMP or IPTC.
- Transparent source pixels are rendered onto a fixed opaque white background before JPEG encoding.
- Multipart uploads use the existing endpoint contract and `file` field, but the request body is file-backed instead of `readBytes()`-backed.
- Prepared upload files live in an app cache subdirectory with opaque `.jpg` names and are deleted after success, failure or cancellation. Original user-selected content is not deleted.
- Reals intentionally disables Android cloud backup and device-transfer backup for application-managed data because the app handles sensitive dating-profile and session-related state.
- Firebase App Check is handled centrally by the shared Reals API client and is independent of Android photo preprocessing.

## Post-MVP

- Ordering should use a dedicated backend endpoint, for example `PUT /api/me/profile/photos/order`, with a list of `photoIds`.
- Media optimization should add backend-generated image variants, for example original, thumbnail, and preview URLs.
- Android should render backend-generated thumbnails/previews once the backend exposes them.
- Direct-to-storage upload remains a post-MVP option only if product or infrastructure needs justify it.
