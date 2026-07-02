# RecallAI

<p align="center">
  <img src="logo_1-removebg-preview.png" alt="RecallAI logo" width="120" />
</p>

<p align="center">
  <strong>AI-powered memory & care companion for Alzheimer's and dementia</strong><br/>
  Final Year Project · Institute of Space Technology · EE&CS
</p>

<p align="center">
  <a href="https://github.com/SaadNasirr/RecallAI-Fullstack">github.com/SaadNasirr/RecallAI-Fullstack</a>
</p>

---

## About

RecallAI helps people with memory difficulties **store, retrieve, and interact** with personal memories using semantic AI, voice, and vision. A linked **caregiver** experience supports monitoring, task coordination, geofencing, and alerts.

The system is a **multimodal Final Year Project (FYP)**: one dual-role Kotlin Android app (patient + caregiver), a Node.js/TypeScript API, MongoDB, and an optional Python voice sidecar. Groq, Gemini, and Hugging Face power chat, recall, memory analysis, and vision.

**Tagline:** *Helping you remember what really matters.*

**Authors:** [Ansar Hayat](https://github.com/ansarhayat9) · [Muhammad Saad Nasir](https://github.com/SaadNasirr)  
**Supervisor:** Dr. Komal Nain Sukhia · IST Islamabad

---

## Features

### Patient (Android)

| Module | Description |
|--------|-------------|
| **Therapist chat** | Groq/Gemini conversational AI; voice mic → STT → chat → TTS; natural-language reminders (`ReminderNlp`) |
| **Recall assistant** | RAG over memories — ask questions, get short grounded answers |
| **Memory bank** | Local Room cache + MongoDB cloud sync; LLM analysis (`event`, `habit`, tags, `eventTime`) |
| **Flashcards** | 3D flip cards for memory practice |
| **Daily schedule** | Today (morning / afternoon / evening), alarms, reminders, **Coming up** for tomorrow |
| **Face insights** | Camera/gallery analysis, mood display, live recognition, face enrollment |
| **Object locator** | Photo + optional voice → AI guidance ("where is it?") |
| **Geofencing** | Safe zones, maps, location refresh, caregiver alerts |
| **Medication & routines** | Schedules, adherence logging, caregiver escalations |
| **People directory** | Known persons with enrollment and anti-duplicate upsert |
| **Emergency assist** | Patient-triggered emergency flow to linked caregivers |

### Caregiver (same Android app, caregiver login)

| Module | Description |
|--------|-------------|
| **Watchlist & alerts** | Patient risk view and recommended actions |
| **Care tasks** | Assign tasks with priority, due date, notifications |
| **Geofence management** | Create, edit, toggle, and delete safe zones |
| **Patient linking** | QR scan, invite codes, approve/reject requests |
| **Rules & presets** | Balanced / strict / relaxed escalation |
| **Shared memories** | View patient remote memories |

### Backend (Node.js)

- JWT auth (patient & caregiver roles, linked-patient scoping)
- Chat sessions + Inngest async workflows
- Memory CRUD with Hugging Face embedding pipeline
- Recall RAG (`POST /recall/chat`)
- Vision: object locator, face analyze / recognize / enroll
- Voice proxy to Python sidecar (optional)
- Care tasks, geofence events, mood logging, FCM push (optional)

---

## Tech stack

### Mobile

| Category | Technologies |
|----------|--------------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM, Hilt DI, Repository pattern |
| Local DB | Room (memories, reminders, alarms, moods, faces) |
| Network | Retrofit, OkHttp, Moshi |
| Camera / ML | CameraX, ML Kit Face Detection |
| Location | Google Play Services, Geofencing, Maps Compose SDK |
| Notifications | AlarmManager, Firebase Cloud Messaging (optional) |
| Build | Gradle 8.x, KSP, KAPT · minSdk 24 · targetSdk 34 |

### Backend

| Category | Technologies |
|----------|--------------|
| Runtime | Node.js 18+, Express, TypeScript |
| Database | MongoDB, Mongoose |
| Auth | JWT, bcrypt |
| AI — chat | Groq (`llama-3.1-8b-instant`), Google Gemini (fallback) |
| AI — memory | Groq (`llama-3.3-70b-versatile`), Hugging Face (`all-MiniLM-L6-v2`) |
| AI — vision | Groq Llama 4 Scout (configurable via env) |
| Async | Inngest |
| Uploads | Multer |
| Push | Firebase Admin SDK (optional) |

### Voice sidecar (optional, port 8000)

| Category | Technologies |
|----------|--------------|
| Framework | FastAPI, Uvicorn |
| STT | faster-whisper |
| TTS | pyttsx3 |

### AI model configuration (env overrides)

| Variable | Default |
|----------|---------|
| `MEMORY_ANALYSIS_MODEL` | `llama-3.3-70b-versatile` |
| `RECALL_REASONING_MODEL` | `llama-3.1-8b-instant` |
| `OBJECT_VISION_MODEL` | `meta-llama/llama-4-scout-17b-16e-instruct` |
| `FACE_VISION_MODEL` | same as object vision |
| `AUDIO_TRANSCRIBE_MODEL` | `whisper-large-v3` |

Verify at runtime: `GET /health/models`

---

## Architecture

```
┌─────────────────────────┐     HTTPS/JWT      ┌──────────────────────────┐
│  Android App (one APK)  │ ◄────────────────► │  Node.js API  :3001      │
│  Patient or Caregiver   │                    │  Express + TypeScript    │
│  role at login          │                    │  MongoDB                 │
│  Room local cache       │                    └───────┬──────────┬─────────┘
└───────────┬─────────────┘                            │          │
            │                                   ┌──────▼──┐  ┌────▼─────┐
            │                                   │ Groq /  │  │ Hugging  │
            │                                   │ Gemini  │  │ Face HF  │
            │                                   └─────────┘  └──────────┘
            │
            └──────────────────────────────────► ┌──────────────────────────┐
                                               │  Voice sidecar  :8000    │
                                               │  FastAPI STT/TTS         │
                                               └──────────────────────────┘
```

**Memory flow:** save → Groq analysis → HF embed → MongoDB → Room sync → RAG recall on query  
**Voice flow:** mic → `POST /api/voice/stt` → chat → `POST /api/voice/tts`  
**Face flow:** CameraX → ML Kit detect → `POST /api/face/recognize` or local cosine match

---

## Project structure

```
RecallAI-Fullstack/
├── android/                    # Kotlin / Jetpack Compose app
│   ├── app/src/main/java/...   # Screens, ViewModels, repositories
│   ├── gradle.properties.example
│   ├── SECURITY.md
│   └── PROJECT_FULL_DOCUMENTATION.md
├── backend/                    # Node.js API
│   ├── src/
│   │   ├── controllers/
│   │   ├── routes/
│   │   ├── utils/
│   │   └── config/modelConfig.ts
│   └── scripts/                # devAll.js, killPort.js
├── docs/                       # Posters and project docs
├── logo_1-removebg-preview.png
└── README.md
```

---

## Development timeline

| Phase | Period | Milestones |
|-------|--------|------------|
| **Foundation** | Mar 2026 | RecallAI3 scaffold; MongoDB; therapist chat; JWT; Room memories |
| **Core modules** | Apr 2026 | Recall RAG, object locator, geofencing, face recognition, voice STT/TTS, Cloudflare tunnel |
| **AI & memory** | Apr–May 2026 | Groq + Gemini fallback; HF embeddings; cloud memory sync; care tasks; medication alarms |
| **Polish & merge** | Jun 2026 | Partner merge; mindcare UI; schedule + Coming up; Face Insights; GitHub publish |

---

## Quick start

### Prerequisites

- Node.js 18+, npm
- MongoDB (local or Atlas)
- Android Studio (API 34, minSdk 24)
- API keys: **Groq**, **Hugging Face**; **Gemini** optional
- Google Maps API key (geofence maps)
- Firebase `google-services.json` (optional, for push)
- Python 3 + voice sidecar (optional, for local STT/TTS)

### 1. Clone

```bash
git clone https://github.com/SaadNasirr/RecallAI-Fullstack.git
cd RecallAI-Fullstack
```

### 2. Backend

```bash
cd backend
npm install
```

Create `backend/.env` (never commit):

```env
MONGODB_URI=mongodb://127.0.0.1:27017/recallai
JWT_SECRET=your-long-random-secret
GROQ_API_KEY=gsk_...
HF_TOKEN=hf_...
GEMINI_API_KEY=
PORT=3001
FIREBASE_SERVICE_ACCOUNT_PATH=
```

```bash
npm run dev          # API only
npm run dev:all      # API + Cloudflare tunnel + voice sidecar
```

Health check: `http://localhost:3001/health`

### 3. Android

1. Open `android/` in Android Studio.
2. Copy `gradle.properties.example` → `gradle.properties`.
3. For a physical device on LAN:

   ```properties
   RECALLAI_DEVICE_BASE_URL=http://YOUR_LAN_IP:3001
   GOOGLE_MAPS_API_KEY=your_key
   ```

4. Add your own `android/app/google-services.json` if using Firebase.
5. Run on emulator (`10.0.2.2:3001`) or a physical device.

`npm run dev:all` can auto-write a Cloudflare tunnel URL into `gradle.properties` for device testing.

---

## API reference

| Area | Endpoints |
|------|-----------|
| **Auth** | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| **Chat** | `GET/POST /chat/sessions`, `POST /chat/sessions/:id/messages` |
| **Memory** | `GET /memory`, `POST /memory`, `DELETE /memory/:id` |
| **Recall** | `POST /recall/chat` |
| **Voice** | `POST /api/voice/stt`, `POST /api/voice/tts` |
| **Vision** | `POST /api/locator`, `POST /api/face/enroll`, `POST /api/face/recognize` |
| **Care** | `/care/tasks`, `/care/qr/*`, `/care/invite/*`, `/care/emergency/trigger`, `/care/alerts/inbox` |
| **Geofence** | `POST /geofence/create`, `GET /geofence/my-zones`, `POST /geofence/event` |
| **Activity / Mood** | `POST /api/activity`, `POST /api/mood` |
| **Health** | `GET /health`, `GET /health/models` |

---

## Key design decisions

1. **Backend-first AI** — STT, TTS, chat, recall, and vision run through Node so API keys stay server-side.
2. **Hybrid memory** — Room for offline cache; MongoDB as source of truth; HF embeddings for semantic search.
3. **Provider fallbacks** — Groq primary; Gemini when Groq fails; deterministic recall fallback if no context matches.
4. **Tunnel dev workflow** — Cloudflare quick tunnel auto-writes URL to `gradle.properties` for physical devices.
5. **On-device + cloud face** — ML Kit for real-time detection; server enrollment for known identities.
6. **Dual-role single app** — One APK; patient or caregiver role selected at login with separate navigation graphs.

---

## UI theming

Mindcare-inspired design (gradient canvas, pastel cards, black nav pill). Experimental layouts can be reverted via flags in:

- `DashboardLayout.kt` → `USE_MINDCARE_STYLE`
- `PatientCareUiLayout.kt` → `USE_LIVELY_CARE_UI`
- `RecallUiLayout.kt` → `USE_LIVELY_RECALL_UI`

---

## Security

Never commit secrets. `.gitignore` excludes `backend/.env`, `android/local.properties`, `google-services.json`, and keystores.

Each developer copies `gradle.properties.example` and creates their own `.env`. See [android/SECURITY.md](android/SECURITY.md).

---

## Documentation

| Document | Contents |
|----------|----------|
| [README.md](README.md) | Project overview (this file) |
| [android/PROJECT_FULL_DOCUMENTATION.md](android/PROJECT_FULL_DOCUMENTATION.md) | Technical deep-dive: APIs, flows, demo checklist |
| [android/SECURITY.md](android/SECURITY.md) | Secrets and key rotation |
| [android/CLOUD_BACKEND_SETUP.md](android/CLOUD_BACKEND_SETUP.md) | Backend and tunnel setup |

---

## Demo checklist

1. Start stack: `npm run dev:all` → confirm `GET /health` returns OK.
2. Register/login as **patient** → therapist chat (text + voice).
3. Save a memory → query recall assistant.
4. Object locator (camera photo).
5. Face insights (live scan or gallery).
6. Geofence map + zone refresh.
7. Add reminder/alarm → check Today / Coming up schedule.
8. Register/login as **caregiver** → link patient → assign care task → verify patient sees it.

---

## Authors

| Name | GitHub | Role |
|------|--------|------|
| **Ansar Hayat** | [ansarhayat9](https://github.com/ansarhayat9) | Android, schedule, Face Insights, integration |
| **Muhammad Saad Nasir** | [SaadNasirr](https://github.com/SaadNasirr) | Backend, caregiver modules, thesis, repo host |

**Supervisor:** Dr. Komal Nain Sukhia · Department of EE&CS · Institute of Space Technology, Islamabad

---

## License

Academic FYP project. Add a public license here if you open-source beyond university submission.
