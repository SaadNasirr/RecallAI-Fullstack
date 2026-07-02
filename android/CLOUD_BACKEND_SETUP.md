## One-Time Setup For Real Phones

To run the app on any Wi-Fi/mobile network without changing IPs every time:

1. Deploy backend to a public URL (Render/Railway/Fly), for example:
   `https://recallai-backend.onrender.com`
2. Set cloud URLs in `gradle.properties`:
   - `RECALLAI_STAGING_BASE_URL=https://recallai-staging.onrender.com` (optional)
   - `RECALLAI_PROD_BASE_URL=https://recallai-backend.onrender.com`
3. Build/install by flavor:
   - Staging: `./gradlew :app:assembleStagingDebug`
   - Production: `./gradlew :app:assembleProdRelease`

### Runtime URL selection

- `dev` flavor:
  - Emulator -> `EMULATOR_BASE_URL` (`10.0.2.2`)
  - Real phone -> `DEVICE_BASE_URL` (LAN development URL)
- `staging` flavor:
  - Uses `RECALLAI_STAGING_BASE_URL` if provided
  - Falls back to LAN `DEVICE_BASE_URL` if empty
- `prod` flavor:
  - Uses `RECALLAI_PROD_BASE_URL` if provided
  - Falls back to LAN `DEVICE_BASE_URL` if empty

After `RECALLAI_STAGING_BASE_URL`/`RECALLAI_PROD_BASE_URL` are set, those flavors work on any network without IP changes.
