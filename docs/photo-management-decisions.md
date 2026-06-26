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

## Post-MVP

- Ordering should use a dedicated backend endpoint, for example `PUT /api/me/profile/photos/order`, with a list of `photoIds`.
- Media optimization should add backend-generated image variants, for example original, thumbnail, and preview URLs.
- Android should render backend-generated thumbnails/previews once the backend exposes them.
- Direct-to-storage upload remains a post-MVP option only if product or infrastructure needs justify it.
