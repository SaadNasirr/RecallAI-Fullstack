# RecallAI Project Master Documentation

Last updated: 2026-04-29

## 1) Project Summary

RecallAI is an AI-powered Alzheimer/dementia support system with:

- An Android mobile app (`RecallAI3`) built in Kotlin + Jetpack Compose.
- A Node.js/TypeScript backend (`ai-therapist-agent-backend-main`).
- A local Python voice sidecar (`recallai-voice-sidecar`) for STT/TTS.

The product supports both **Patient** and **Caregiver** roles and includes:

- Therapist chat
- Recall assistant (memory retrieval)
- Memory bank
- Object locator intelligence
- Face analysis + live recognition + enrollment
- Geofencing and risk signaling
- Medication scheduling
- People memory book
- Caregiver dashboard features (watchlist, rules, alert center, care coordination)

---

## 2) Repository Structure

### Android App
- Root: `C:\Users\Dell\Desktop\RecallAI3`
- Main app module: `app`
- Navigation shell and routes: `MainActivity.kt`
- Core screens: `app/src/main/java/com/example/recallai/...`

### Backend
- Root: `C:\Users\Dell\Desktop\ai-therapist-agent-backend-main\ai-therapist-agent-backend-main`
- Entry point: `src/index.ts`
- Routes: `src/routes`
- Controllers: `src/controllers`
- AI utilities: `src/utils`
- Model config: `src/config/modelConfig.ts`

### Voice Sidecar
- Root: `C:\Users\Dell\Desktop\recallai-voice-sidecar`
- Entry point: `main.py`
- FastAPI endpoints: `/health`, `/stt`, `/tts`

---

## 3) Technology Stack

## Mobile (Android)
- Kotlin, Coroutines
- Jetpack Compose (Material 3)
- Navigation Compose
- Hilt DI
- Room local database
- Retrofit + OkHttp + Moshi
- CameraX
- ML Kit (face/object libraries)
- Google Play Services Location + Geofencing
- Google Maps Compose SDK
- AlarmManager/Notifications

## Backend
- Node.js + Express + TypeScript
- MongoDB + Mongoose
- JWT auth
- Multer for multipart uploads
- Inngest event workflows
- Groq SDK
- Gemini SDK
- HuggingFace Inference (embeddings)

## Voice Sidecar
- FastAPI
- faster-whisper (STT)
- pyttsx3 (TTS)

---

## 4) Mobile App Architecture

High-level pattern:

- **UI layer**: Compose screens
- **State layer**: ViewModels
- **Data layer**: Repositories (network + Room + local prefs)
- **DI**: Hilt `AppModule`

Key app navigation:

- Auth flow: Splash -> Login -> Role Selection
- Role shells:
  - Patient shell with bottom tabs: Home, Chat, Face, Memo, Recall
  - Caregiver shell with bottom tabs: Home, Watch, Rules, Memo, Zones

Bottom bar behavior:

- Uses `findStartDestination` + `launchSingleTop` + `restoreState`.
- Styled with rounded container and theme-adaptive colors.

---

## 5) Role Design

## Patient

- Focus: daily support, memory, therapist interactions, orientation help.
- Geofencing in patient mode is simplified for "lost support":
  - refresh location
  - open current map
  - navigate home zone
  - no heavy zone-management controls

## Caregiver

- Focus: supervision, escalation, rules, alerts, task coordination.
- Geofencing in caregiver mode includes full management:
  - create/edit/remove zones
  - enable/disable background geofencing
  - alert test and monitoring controls

---

## 6) Core Mobile Features and Flows

## 6.1 Therapist Chat

Flow:
1. User opens chat session.
2. App creates/fetches backend chat session.
3. Message sent to `/chat/sessions/{id}/messages`.
4. Backend returns therapist response and analysis.
5. UI renders compact WhatsApp-style conversation.
6. Optional voice:
   - STT: `/api/voice/stt`
   - TTS: `/api/voice/tts`

UX implemented:
- message-first layout
- clear composer
- timestamps
- typing state
- read aloud / copy / save actions

## 6.2 Memory Bank + Recall Assistant

Flow:
1. Memories saved locally + remote `/memory`.
2. Recall query sent to `/recall/chat`.
3. Backend performs embedding search + intent filter.
4. LLM synthesizes answer from retrieved memories.

## 6.3 Object Intelligence (Locator)

Flow:
1. User uploads image (and optionally voice).
2. App sends multipart request to `/api/locator`.
3. Backend:
   - optional voice transcription
   - vision analysis with configured model
4. Response returned as natural language guidance.

## 6.4 Face Insights + Recognition

Capabilities:
- image-based analysis (`/api/face`)
- live recognition (`/api/face/recognize`)
- enrollment (`/api/face/enroll`)

Threshold logic:
- Local vector recognition threshold in app: `0.90` cosine.
- Backend recognition threshold: `0.74` cosine.

UI confidence bands:
- High: >=85%
- Moderate: >=60%
- Low: <60%

## 6.5 Geofencing

Flow:
1. Permission check (foreground/background where needed).
2. Live location from FusedLocationProvider.
3. Safe zones saved and managed.
4. Background geofences registered via `GeofencingClient`.
5. Broadcast receiver handles ENTER/EXIT/DWELL events.
6. Local notifications shown on transitions.
7. Backend sync:
   - `/api/activity` for geofence state
   - `/api/mood` for risk signal

Map support:
- In-app map with marker + radius circles
- open in Google Maps / navigation intents

## 6.6 Medication Scheduler

Flow:
1. Medication and time entered.
2. Exact alarm scheduled for daily reminder.
3. Notification shown at due time.
4. Alarm reschedules for next day.

## 6.7 People Memory Book

Capabilities:
- add/edit/remove person
- search/filter
- anti-duplicate upsert behavior by name

## 6.8 Caregiver Operations

- Watchlist with risk and recommended actions
- Caregiver rules and presets (balanced/strict/relaxed)
- Alert center with top-priority resolution guidance
- Care coordination tasking (assignee, priority, due date)

---

## 7) Backend Architecture

Entry point: `src/index.ts`

Startup responsibilities:
- loads env vars
- configures CORS, JSON parsing, logging, helmet
- registers Inngest endpoint `/api/inngest`
- mounts route groups
- provides `/health` and `/health/models`
- connects MongoDB
- starts HTTP server on port `3001` (default)

Main route mounts:
- `/auth`
- `/chat`
- `/api/mood`
- `/api/activity`
- `/memory`
- `/recall`
- `/api/locator`
- `/api/voice`
- `/api/face`

Auth:
- JWT bearer
- middleware injects `req.user`
- protected routes enforced where required

---

## 8) Backend APIs (Primary)

## Authentication
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout` (auth)
- `GET /auth/me` (auth)

## Chat
- `GET /chat/sessions` (auth)
- `POST /chat/sessions` (auth)
- `GET /chat/sessions/:sessionId` (auth)
- `POST /chat/sessions/:sessionId/messages` (auth)
- `GET /chat/sessions/:sessionId/history` (auth)

## Memory + Recall
- `GET /memory` (auth)
- `POST /memory` (auth)
- `DELETE /memory/:id` (auth)
- `POST /recall/chat` (auth)

## Voice
- `POST /api/voice/stt`
- `POST /api/voice/tts`

## Vision/Face
- `POST /api/locator` (multipart image/audio/query)
- `POST /api/face` (multipart image)
- `POST /api/face/recognize` (multipart image)
- `POST /api/face/enroll` (multipart image + name)

## Geofence-related telemetry
- `POST /api/activity` (auth)
- `POST /api/mood` (auth)

## Health
- `GET /health`
- `GET /health/models`

---

## 9) AI Models and Providers

Configured in `src/config/modelConfig.ts`:

- `objectVision`: `meta-llama/llama-4-scout-17b-16e-instruct` (default)
- `faceVision`: defaults to `objectVision`
- `audioTranscribe`: `whisper-large-v3` (backend-side option)
- `recallReasoning`: `llama-3.1-8b-instant`
- `memoryAnalysis`: `llama-3.3-70b-versatile`

Other model/provider usage:

- Therapist chat:
  - primary: Groq `llama-3.1-8b-instant`
  - fallback: Gemini `gemini-2.0-flash`
  - final fallback: local deterministic therapeutic responder
- Embeddings:
  - HF `sentence-transformers/all-MiniLM-L6-v2`
  - local deterministic fallback embedding if HF fails
- Voice sidecar STT:
  - faster-whisper model default `small` (override via env)
- Voice sidecar TTS:
  - pyttsx3 local synthesis

---

## 10) Data and Persistence

## Mobile
- Room DB for local memories/state.
- SharedPreferences for flags and config (e.g., backend override, geofence flags).

## Backend
- MongoDB collections for users, sessions, chat sessions, moods, activities, memories, etc.
- Local file storage in backend `uploads/` for temporary media and face profiles JSON.

## Face profile stores
- App-local vector profiles via repository.
- Backend persistent profile embeddings in `uploads/face_profiles.json`.

---

## 11) Runtime and Deployment Flow

## Recommended development runtime
Use backend `dev:all` script:
- starts Node backend
- starts cloudflared tunnel
- starts Python voice sidecar
- syncs tunnel URL into Android `gradle.properties` (`RECALLAI_TUNNEL_BASE_URL`)

Script: `scripts/devAll.js`

## Android base URL resolution
App chooses base URL in this order:
1. emulator URL (`10.0.2.2`)
2. tunnel URL (if explicitly fixed)
3. device LAN URL (`RECALLAI_DEVICE_BASE_URL`)

---

## 12) Environment Variables / Config Keys

Do not share secrets publicly. Share key names only.

Common backend keys:
- `PORT`
- `MONGODB_URI`
- `JWT_SECRET`
- `GROQ_API_KEY`
- `GEMINI_API_KEY`
- `HF_TOKEN`
- `CORS_ORIGINS`
- model override keys:
  - `OBJECT_VISION_MODEL`
  - `FACE_VISION_MODEL`
  - `AUDIO_TRANSCRIBE_MODEL`
  - `RECALL_REASONING_MODEL`
  - `MEMORY_ANALYSIS_MODEL`

Mobile `gradle.properties` keys:
- `RECALLAI_DEVICE_BASE_URL`
- `RECALLAI_TUNNEL_BASE_URL`
- `GOOGLE_MAPS_API_KEY`

Voice sidecar keys:
- `STT_MODEL`
- `STT_LANG`
- `STT_BEAM_SIZE`
- `STT_BEST_OF`

---

## 13) Quality and Stability Notes

- Kotlin daemon instability on Windows addressed by:
  - `kotlin.compiler.execution.strategy=in-process`
  - Kotlin app language/api pinned to `1.9` for KAPT compatibility
- STT reliability improvements:
  - audio bitrate tuned lower in recorder
  - backend STT timeout + retry on transient failures
- Geofence stability:
  - Android 12+ mutable pending intent handling
  - map key checks and fallback behavior

---

## 14) Security and Privacy Notes

- Authentication is JWT-based on protected endpoints.
- Sensitive tokens are expected in environment, not source.
- Media uploads are temporary; backend cleans temp files after processing.
- Patient/caregiver data should be handled under local data governance policy.

---

## 15) End-to-End Example Workflows

## A) Therapist voice message
1. Record voice in app.
2. Upload to `/api/voice/stt`.
3. Transcript sent to chat endpoint.
4. Response returned and displayed.
5. Optional read aloud via `/api/voice/tts`.

## B) Geofence alert lifecycle
1. Caregiver sets zone and enables background geofence.
2. Android transition fires in receiver.
3. Notification shown.
4. App reports status via `/api/activity`.
5. Risk mood signal sent via `/api/mood`.
6. Caregiver dashboards consume this telemetry.

## C) Face identity lifecycle
1. Enroll face with name.
2. Backend stores embedding profile.
3. Live scan samples frame and calls recognize API.
4. Label + confidence returned and shown in UI.
5. User can save insight/memory.

---

## 16) Current Product Scope (Implemented)

- Full patient and caregiver role shells
- AI therapist chat with multi-provider fallback
- Recall memory search and response synthesis
- Object locator with image (+ optional voice context)
- Face analysis, live recognition, and cloud enrollment
- Geofence monitoring + maps + cloud risk/activity sync
- Medication reminders with daily alarm scheduling
- People memory CRUD features
- Caregiver watchlist/rules/coordination/alert center

---

## 17) Demo Checklist (Shareable)

Before presentation:
- Start backend stack (`dev:all`) and confirm `/health` is OK.
- Confirm Android points to reachable backend URL.
- Login from app.
- Test each flagship feature once:
  - chat
  - recall
  - object locator
  - face recognition
  - geofence refresh + map + status sync
  - medication reminder setup

During demo:
- Show patient flow briefly.
- Show caregiver intelligence flow and monitoring.
- Show backend logs for `/api/activity` and `/api/mood` to prove live telemetry.

---

## 18) File Ownership / Update Guidance

Maintain this document whenever:
- model IDs/provider logic changes
- routes/endpoints change
- major UX/navigation changes
- deployment flow changes

Recommended owner: project lead / release engineer.

