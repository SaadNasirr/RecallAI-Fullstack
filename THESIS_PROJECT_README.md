# RecallAI — Development Log (Summary — see THESIS_COMPLETE_README.md for full history)

> **For the single file with ALL Cursor chats (775 turns, 2.7 MB):** use [`THESIS_COMPLETE_README.md`](THESIS_COMPLETE_README.md)

# RecallAI — Complete Development Log (Thesis Reference)

**Final Year Project (FYP)**  
**Author:** Ansar Hayat  
**GitHub:** [github.com/ansarhayat9/RecallAI](https://github.com/ansarhayat9/RecallAI)  
**Document purpose:** Chronological record of every major development decision, feature, problem, fix, addition, and removal — suitable for thesis methodology, implementation, and discussion chapters.

---

## Document metadata

| Field | Value |
|-------|-------|
| **Project name** | RecallAI — AI-powered memory & dementia care assistant |
| **Development start (evidence)** | **15 March 2026** (first confirmed MongoDB + backend run in project logs) |
| **Cursor AI-assisted sessions** | **22 April 2026 → 7 June 2026** (9 transcript files, 776 user turns) |
| **Report compiled** | **7 June 2026** |
| **Primary codebases** | `RecallAI3` (original FYP Android) → `RecallAI-main` (partner merge) + `ai-therapist-agent-backend-main` |
| **Exhaustive appendix** | [`../RECALLAI_TRANSCRIPT_TIMELINE.md`](../RECALLAI_TRANSCRIPT_TIMELINE.md) (67,269 lines, turn-by-turn) |
| **Executive summary** | [`../THESIS_EXECUTIVE_SUMMARY.md`](../THESIS_EXECUTIVE_SUMMARY.md) |
| **Re-runnable parser** | [`../parse_transcripts.py`](../parse_transcripts.py) |

> **Note on completeness:** Cursor stores agent transcripts per workspace. This log merges **all recoverable transcripts** from three workspaces. Work before 22 April 2026 is reconstructed from embedded timestamps inside those transcripts (e.g. MongoDB logs dated March 15) and from `PROJECT_FULL_DOCUMENTATION.md` (last updated 29 April 2026). If you have older chats outside Cursor, attach them and re-run `parse_transcripts.py`.

---

## 1. Project vision (unchanged from day one)

RecallAI is a **multimodal AI memory assistant** for people with Alzheimer's/dementia and their **caregivers**. Core goals:

- **Semantic memory** — store, categorize, and retrieve memories using embeddings + LLM reasoning
- **Voice-first interaction** — STT/TTS in therapist chat and tools
- **Multimodal AI** — text, audio, image (object locator, face analysis)
- **Dual roles** — Patient (daily support) and Caregiver (monitoring, tasks, geofences)
- **Award-winning mobile UX** — polished Jetpack Compose UI for FYP demo

---

## 2. Repository & folder evolution

| Phase | Location | Role |
|-------|----------|------|
| Original FYP | `C:\Users\Dell\Desktop\RecallAI3` | Primary Android app during Mar–May 2026 |
| Backend | `C:\Users\Dell\Desktop\ai-therapist-agent-backend-main` | Node/TS API (port 3001) |
| Voice sidecar | `C:\Users\Dell\Desktop\recallai-voice-sidecar` | FastAPI STT/TTS (port 8000) |
| Desktop launcher (old) | `Start_RecallAI_Backend.bat` | One-click backend + tunnel + voice |
| Partner merge | `C:\Users\Dell\Downloads\RecallAI-main\RecallAI-main` | Unified repo from partner |
| Desktop launcher (new) | `Start_RecallAI_Backend_Main.bat` | Same stack, points at RecallAI-main Android |
| FullStack sync | `C:\Users\Dell\Desktop\RecallAI-FullStack` | Desktop copy updated for demos |
| GitHub remote | `https://github.com/ansarhayat9/RecallAI.git` | Partner collaboration |

---

## 3. Technology stack (final state)

### Mobile (Android)
| Technology | Use |
|------------|-----|
| Kotlin 2.0 | Language |
| Jetpack Compose + Material 3 | UI |
| Hilt | Dependency injection |
| Room | Local cache (memories, reminders, alarms) |
| Retrofit + OkHttp + Moshi | REST client |
| CameraX | Live camera (face, object) |
| ML Kit | On-device face detection |
| Google Maps Compose | Geofence maps |
| AlarmManager + Notifications | Medication / care reminders |
| Gradle 8.x | Build |

### Backend
| Technology | Use |
|------------|-----|
| Node.js + Express + TypeScript | API server (:3001) |
| MongoDB + Mongoose | Users, chat, memories, care tasks |
| JWT | Auth |
| Groq SDK | Therapist chat, recall reasoning, vision |
| Google Gemini | Chat fallback |
| Hugging Face Inference | `all-MiniLM-L6-v2` embeddings |
| Inngest | Async chat workflows |
| Multer | Image/audio uploads |
| Firebase Admin | FCM push (optional) |
| cloudflared | Dev tunnel for physical devices |

### Voice sidecar
| Technology | Use |
|------------|-----|
| FastAPI + Uvicorn | HTTP server (:8000) |
| faster-whisper | Speech-to-text |
| pyttsx3 | Text-to-speech |

### AI models (configurable via `.env`)
| Variable | Default model |
|----------|---------------|
| `MEMORY_ANALYSIS_MODEL` | `llama-3.3-70b-versatile` |
| `RECALL_REASONING_MODEL` | `llama-3.1-8b-instant` |
| `OBJECT_VISION_MODEL` | `meta-llama/llama-4-scout-17b-16e-instruct` |
| `FACE_VISION_MODEL` | Same as object vision |
| `AUDIO_TRANSCRIBE_MODEL` | `whisper-large-v3` |

---

## 4. Chronological development timeline

### Phase 0 — Project inception & scaffolding (March 2026)

**Estimated dates:** 15–25 March 2026  
**Evidence:** MongoDB connection log `2026-03-15T11:54:05`; recall query log `2026-03-25T14:12:35`

#### What we did
- Defined FYP scope: build Android app **from scratch** with caregiver + patient modules, voice, object detection, facial recognition, chatbot — all connected to existing backend
- Set up Gradle (Kotlin 2.0, KAPT, Hilt, Compose)
- Connected **therapist chat** to backend (`/chat/sessions`, `/chat/sessions/:id/messages`)
- Installed and configured **MongoDB** for backend persistence
- Implemented basic navigation: Splash → Login → Role Selection → Patient/Caregiver home
- Room DB for local memory display (`MyMemoriesScreen`)

#### Problems faced
| Problem | Resolution |
|---------|------------|
| `Redeclaration: RegisterRequest` | Removed duplicate data class in `AuthApi.kt` |
| `ChatRepository cannot be provided` (Hilt) | Added `@Provides` in `AppModule` |
| `EADDRINUSE :::3001` | Killed stale process (`taskkill /PID`) |
| `Unresolved reference 'remember'` | Added missing Compose imports |
| User confusion about project state | Clarified RecallAI3 already had therapist chat; continued building **in RecallAI3** (Option 2) |

#### Added
- `RecallAI3` Android project structure
- Auth flow (login)
- Chat screen + `ChatViewModel` + `ChatRepository`
- MongoDB connection in backend
- Basic patient/caregiver home shells

#### Removed
- Duplicate API model definitions
- Plans to rebuild therapist chat from zero (already existed)

---

### Phase 1 — Auth, recall, and backend parity (April 1–21, 2026)

**Cursor session:** `adef5366` (22 Apr) + early `a325dd98` (25 Apr)  
**Evidence:** Backend logs April 9, 21, 23

#### What we did
- **Sign-up + logout** before/after login
- **Android–browser parity mandate:** integrate geofencing, caregiver module, audio, object detection, facial recognition, semantic recall — without breaking chat/voice/Room memories
- **Recall assistant** connected to `POST /recall/chat` with vector search
- **Groq-first therapist chat** with Gemini fallback (replaced Inngest-only path that returned `[object Object]`)
- `killPort.js` predev script to fix port conflicts
- UI theme pass — moved away from “beginner empty cards”
- Researched [NeuralTrace_Frontend](https://github.com/abdullamaqsood/NeuralTrace_Frontend) style — implemented **Option 3** glass/gradient design across screens
- Caregiver dashboard scroll + layout redesign

#### Problems faced
| Problem | Resolution |
|---------|------------|
| Therapist chat returning errors / `[object Object]` | Fixed response parsing; Groq direct path + Gemini fallback |
| Recall dumping entire chat history | Tuned recall prompt to **1–2 line answers** for specific queries |
| `EADDRINUSE` (repeated) | `npm run predev` → `killPort.js` |
| `IllegalArgumentException: Only VectorDrawables...` | Replaced invalid XML drawable refs with PNG/WebP assets |
| `ElevatedCard` / `animateFloat` compile errors | Fixed Compose API usage in `UiKit.kt` |
| App opens and closes (crash on launch) | Fixed painter resource types |
| Invalid API key messages (Groq/Gemini) | `.env` validation, key format checks, provider fallback chain |
| App slow / high latency | Identified heavy ML on main thread; began backend-first proxy pattern |

#### Added
- Register screen, logout in settings
- `RecallAssistantScreen`, `RecallRepository`
- Groq chat integration in `chat.ts`
- `UiKit.kt`, `GlassCard`, gradient themes
- NeuralTrace-inspired design system
- `scripts/killPort.js`

#### Removed
- Inngest as **sole** chat path (kept for optional async, not user-visible failures)
- Plain Material cards-only theme
- Verbose recall responses

---

### Phase 2 — Award-winning polish & live AI modules (April 22–27, 2026)

**Cursor sessions:** `a325dd98` (continued), `5e131b09` (26–27 Apr)  
**413+ messages in later June continuation of same session ID**

#### What we did
- **Live camera face recognition** (not gallery-only) — CameraX preview → ML Kit → backend `/api/face/recognize`
- **Object Intelligence** — camera capture → `/api/locator`
- **Geofencing** — zone create/edit, maps, background transitions, caregiver alerts
- **Voice on real Android device** via Cloudflare quick tunnel (`dev:all`, `devAll.js`)
- Auto-write tunnel URL to `gradle.properties` (`RECALLAI_TUNNEL_BASE_URL`)
- **Model env vars** documented: `OBJECT_VISION_MODEL`, `FACE_VISION_MODEL`, etc. + `GET /health/models`
- Full app audit: every button, scroll, screen from signup → all modules
- Therapist chat UI — WhatsApp-style bubbles, readable answer cards for all tools
- Logo v1 — Blue/beige minimalist brand logo on splash + auth
- Typography fixes — role selection “RecallAI is here to help” on one line
- Performance pass on laggy buttons (ANR logs `2026-04-24`)

#### Problems faced
| Problem | Resolution |
|---------|------------|
| No therapist chat UI / broken buttons | Fixed navigation routes, composer visibility, scroll on Memories |
| Geofence “how do I add zone?” | Added caregiver zone management UI |
| Groq **429 rate limit** on face vision | Retry/backoff; moved toward ML Kit on-device detection |
| **ANR** on MainActivity (5s input timeout) | Reduced main-thread work; optimized recompositions |
| Cloudflare DNS timeout | `TUNNEL_PROTOCOL=quic`, `TUNNEL_EDGE_IP_VERSION=auto` on Windows |
| Tunnel proxy `context canceled` on face upload | Increased timeouts; smaller image payloads |
| Physical phone can’t reach PC backend | Cloudflare tunnel → auto-sync URL to Android |
| STT empty on some runs | Verified sidecar on :8000; multipart `audio` field |
| Gemini/Groq “not working” | Fixed `.env` loading (`dotenv override`), provider order |

#### Added
- `devAll.js` orchestration (tunnel + backend + voice + gradle sync)
- `Start_RecallAI_Backend.bat` desktop launcher
- Live camera pipelines for face + object
- `PatientDashboardVisibility` patterns (later)
- Face enrollment + recognition UI
- Care task assignment (caregiver → patient)
- Medication scheduler with daily alarms
- People memory book (CRUD)
- Temporal context prefix in chat (“Current local date/time…”)

#### Removed
- Gallery-only face identification as **sole** path
- Manual IP re-entry every WiFi change (replaced by tunnel automation)
- On-device STT (standardized on backend proxy)

---

### Phase 3 — Cloud memory, face rewrite & GitHub (May – 3 June 2026)

**Cursor session:** `5e131b09` (413 messages, 6.6 MB transcript)

#### What we did
- **Memory migration:** Room local cache + **MongoDB cloud** (`GET/POST /memory`) + HF embeddings
- **Face recognition rewrite:** ML Kit detection + custom 128-dim `FaceDescriptor` embeddings (cosine match)
- Therapist **intent routing** — “Where am I?” / recall queries get guided responses (not raw screen jumps)
- **RecallAI-FullStack** folder on Desktop synced with latest code
- **GitHub push** for partner (`ansarhayat9/RecallAI`)
- **FYP poster** — multiple design iterations
- **Security hardening** — `.gitignore`, `SECURITY.md`, removed hardcoded keys from `gradle.properties`
- `UPLOAD-TO-GITHUB.md` for partner onboarding
- `PROJECT_FULL_DOCUMENTATION.md` master doc (29 Apr 2026, updated in this phase)

#### Problems faced
| Problem | Resolution |
|---------|------------|
| Face camera `FATAL EXCEPTION` (May 16 logs) | CameraX lifecycle fixes, permission gating |
| `google-services.json` missing after merge | Documented per-dev Firebase setup |
| Gradle `R.jar` file lock | `--no-build-cache` retry; close Android Studio |
| Back button lag | `launchSingleTop`, reduced stack depth |
| FAISS vs MongoDB confusion | Clarified: MongoDB stores memories; vectors in MongoDB embedding field |
| Large `/memory` payloads (~53 KB) | Pagination/sync throttling considered; 60s sync debounce |
| FCM disabled warning | Optional `FIREBASE_SERVICE_ACCOUNT_PATH` |

#### Added
- `RemoteMemoryRepository`, cloud sync in `MemoryRepository`
- `EventRemoteScheduleParser` (later, Jun 7)
- Face Insights redesign components
- Care routes (`/care/tasks`, geofence events)
- GitHub repo + secure upload docs

#### Removed
- Backend-only face flow (as primary)
- Hardcoded Gemini key in `aiFunctions.ts`
- Committed `GOOGLE_MAPS_API_KEY` from `gradle.properties` (rotate keys)

---

### Phase 4 — Partner repo integration: RecallAI-main (3–7 June 2026)

**Cursor session:** `8a5756d7` (58 user messages)  
**Workspace:** `c:\Users\Dell\Downloads\RecallAI-main`

#### What we did

##### 4.1 Backend launcher (3 Jun)
- Created `Start_RecallAI_Backend_Main.bat` on Desktop
- Set `RECALLAI_ANDROID_DIR` → `RecallAI-main\android` (was defaulting to RecallAI3)
- Ran `npm install` — fixed `nodemon not recognized`
- Verified stack: Node :3001, Uvicorn :8000, Cloudflare tunnel, MongoDB

##### 4.2 Logo & branding (3–5 Jun) — **10+ iterations**
- New brain logo (`recallai_logo.png`, `untitled design.png`)
- Splash screen: gradient, tagline **“Helping you remember what really matters”**, animated progress bar — **frozen as final** (user mandate)
- App icon / adaptive icons (`mipmap-*`) — crop to show **only brain**, centered, no box
- Toolbar logo: 44×44 dp, `fitCenter`
- **Duplicate resources** build error — deduplicated drawable/mipmap entries

##### 4.3 Mindcare dashboard redesign (5–6 Jun)
- Reference design from shared link (mindcare-style)
- **Revert flags** (say “go back” → set flag `false`):

| Feature | File | Flag |
|---------|------|------|
| Dashboards | `ui/dashboard/DashboardLayout.kt` | `USE_MINDCARE_STYLE` |
| Care schedule | `ui/patient/PatientCareUiLayout.kt` | `USE_LIVELY_CARE_UI` |
| Recall screen | `recallassistant/RecallUiLayout.kt` | `USE_LIVELY_RECALL_UI` |

- Patient + caregiver dashboards: gradient canvas, stat pills, black nav bar
- Role selection: no scroll, fits one screen
- Sign-up / login: animated entry, mindcare colors

##### 4.4 Care schedule & notifications (6–7 Jun)
- `PatientDashboardVisibility.kt` — hide completed care tasks; auto-complete one-time reminders on fire; hide past schedule slots (5 min grace)
- `LivelyPatientCareSections.kt` — Morning/Afternoon/Evening cards with animations
- Themed alarm + reminder dialogs (`MindcareScheduleDialogs.kt`)
- **Bug:** completed tasks/alarms stayed visible forever → state-based visibility fix
- **Schedule ↔ chat gap:** meeting at 5pm tomorrow saved as server `event` memory but **Today** showed “Free”
  - **Root cause 1:** Today only shows today's items (correct for tomorrow's meeting)
  - **Root cause 2:** Chat event memories not wired to schedule
  - **Fix:** `EventRemoteScheduleParser.kt` + **Coming up** card for tomorrow (`upcomingTomorrow` in `PatientHomeUiState`)
  - **Fix:** Broader `ReminderNlp.looksLikeReminderIntent` for “meeting at 5pm”

##### 4.5 Module UI polish (6–7 Jun)
- **Therapist chat:** `MindcareGradientBackground`, transparent scaffold, black top bar
- **Recall assistant:** 3D flip card (`MemoryFlipCard`), lively gradient layout
- **Face Insights:** `FaceInsightsDesignComponents.kt` — stat pills, mood hero, live-scan animations
- **Alarm editor:** Material3 themed time picker (replaced system teal)

##### 4.6 Documentation (7 Jun)
- `README.md` — GitHub project readme
- `THESIS_PROJECT_README.md` — this file
- `RECALLAI_TRANSCRIPT_TIMELINE.md` — full appendix

#### Problems faced (Phase 4)
| Problem | Resolution |
|---------|------------|
| `nodemon not recognized` | `npm install` in `backend/` |
| Wrong Android path in tunnel | `RECALLAI_ANDROID_DIR` env in bat file |
| Logo sizing wars (~2 hours) | Final crop: brain only, centered; splash locked |
| Tomorrow schedule UI broken (one full-width “Free” card) | Replaced 3-card Tomorrow row with **Coming up** list card |
| `slideInVertically` compile errors | Named parameter syntax fix |
| Missing `sp` import in Face Insights | Added import; build succeeded |
| Gradle R.jar lock | `--no-build-cache` compile retry |

#### Added (Phase 4)
- `Start_RecallAI_Backend_Main.bat`
- `EventRemoteScheduleParser.kt`
- `UpcomingScheduleItem`, `LivelyComingUpCard`
- `PatientDashboardVisibility.kt`
- `MindcareKit.kt` design tokens
- `LivelyRecallAssistantScreen.kt` + legacy routers
- `MemoryFlipCard` in `FlashcardScreen.kt`
- `THESIS_PROJECT_README.md`, `parse_transcripts.py`

#### Removed / reverted (Phase 4)
- Duplicate Tomorrow 3-card row (bad layout)
- `scheduleTomorrowMorning/Afternoon/Evening` UI exposure (replaced by `upcomingTomorrow`)
- System default teal time picker in alarm dialog
- Splash screen changes after user approval (frozen)

---

## 5. Feature matrix (implementation status at project end)

| Module | Patient | Caregiver | Backend | Status |
|--------|---------|-----------|---------|--------|
| Auth (login/register/roles) | ✅ | ✅ | JWT | Working |
| Therapist chat + voice | ✅ | — | Groq/Gemini | Working |
| Memory bank (local + cloud) | ✅ | ✅ view | MongoDB + HF embed | Working |
| Semantic recall assistant | ✅ | — | `/recall/chat` | Working |
| Object locator | ✅ | — | `/api/locator` | Working |
| Face analysis | ✅ | — | `/api/face` | Working |
| Live face recognition | ✅ | — | ML Kit + `/api/face/recognize` | Working |
| Face enrollment | ✅ | — | `/api/face/enroll` | Working |
| Geofencing + maps | ✅ | ✅ manage | `/geofence/*`, `/api/activity` | Working |
| Care tasks | ✅ receive | ✅ assign | `/care/tasks` | Working |
| Medication / reminders / alarms | ✅ | — | Local + notifications | Working |
| Schedule (Today + Coming up) | ✅ | — | Local + remote events | Working (post Jun 7 fix) |
| People memory book | ✅ | — | Local + remote | Working |
| Push notifications (FCM) | Optional | Optional | Firebase Admin | Needs service account |
| Cloudflare dev tunnel | Dev only | — | `devAll.js` | Working |

---

## 6. Complete list of problems & resolutions (thesis “Challenges” section)

### Infrastructure & dev environment
1. Port 3001 already in use → `killPort.js` + `predev` script  
2. `nodemon` not found in RecallAI-main → `npm install`  
3. MongoDB not running → documented start procedure  
4. Android emulator vs device networking → `10.0.2.2` vs LAN IP vs Cloudflare tunnel  
5. Tunnel DNS / TLS failures on Windows → QUIC + `edge-ip-version=auto`  
6. Gradle file locks on Windows → close IDE, `--no-build-cache`  
7. `google-services.json` not in repo → per-developer Firebase project  

### Build & compile
8. Hilt missing bindings (`ChatRepository`) → `AppModule` providers  
9. Duplicate `RegisterRequest` / duplicate resources → deduplication  
10. Compose API mismatches (`ElevatedCard`, `animateFloat`, `slideInVertically`) → API updates  
11. Vector drawable vs PNG in `painterResource` → asset type fixes  
12. Missing imports (`remember`, `sp`) → added imports  

### AI / API
13. Therapist chat `[object Object]` / empty replies → Groq direct + Gemini fallback + JSON parse fix  
14. Invalid API key errors → `.env` format validation, never commit keys  
15. Groq 429 rate limits on vision → retry, throttle, ML Kit on-device path  
16. Recall too verbose → prompt tuning for 1–2 line answers  
17. HF embedding provider selection → `hf-inference` auto-select  
18. Large `/memory` response bodies → sync debounce, consider pagination  
19. FCM disabled → optional Firebase service account path  

### Android runtime
20. App crash on launch (painter) → PNG assets  
21. ANR on MainActivity → reduce main-thread ML  
22. Face camera FATAL EXCEPTION → CameraX lifecycle + permissions  
23. Laggy buttons / back navigation → navigation flags, recomposition optimization  
24. No scroll on Memories / caregiver screens → `verticalScroll` + weight fixes  
25. Dead buttons (chat, geofence, assign task) → route wiring audits  

### UI / UX
26. “Beginner empty” theme → NeuralTrace / Mindcare redesign  
27. Logo sizing (~10 iterations) → final brain-only crop; splash locked  
28. Tomorrow schedule showing one stretched “Free” card → **Coming up** card redesign  
29. Meeting tomorrow not in Today schedule → documented + `EventRemoteScheduleParser`  
30. Completed care tasks not disappearing → `PatientDashboardVisibility`  
31. Flip card missing in recall → `MemoryFlipCard` restored  
32. Face Insights visually disjoint → mindcare theme pass  

### Security
33. API keys in `gradle.properties` / `.env` committed → `.gitignore`, `SECURITY.md`, key rotation  
34. Hardcoded Gemini in source → removed, env-only  

### Process / collaboration
35. Cursor chat loss after crash → transcript files in `.cursor/projects/...`  
36. Partner merge path confusion (RecallAI3 vs RecallAI-main) → separate bat launchers + docs  
37. User “where are we?” context loss → `PROJECT_FULL_DOCUMENTATION.md`, this log  

---

## 7. Things added (cumulative)

### Android screens & flows
- Splash (animated, tagline, locked design)
- Login, Register, Role Selection (animated)
- Patient home dashboard (mindcare + legacy)
- Caregiver home dashboard (mindcare + legacy)
- Therapist chat (gradient, voice, reminder NLP)
- Recall assistant (flip card + lively layout)
- Memory bank / flashcards
- Object Intelligence (camera)
- Face Insights (camera + gallery)
- Geofence maps & zone editor
- Patient schedule (Today, Coming up, Alarms, Reminders)
- Care task list (patient + caregiver)
- Medication scheduler
- People directory
- Settings / logout

### Android architecture
- Hilt modules, repositories per domain
- Room entities: memories, reminders, alarms, moods, faces
- `PatientHomeViewModel` schedule builder
- `ReminderNlp` / `ReminderScheduler`
- `CareRemoteScheduleParser`, `EventRemoteScheduleParser`
- `PatientDashboardVisibility`
- Revert-flag layout routers (`*UiLayout.kt`)

### Backend
- Auth, chat, memory, recall, voice, face, locator, care, geofence routes
- `analyzeMemoryContent` (Groq) → `eventTime`, `type`, `tags`
- Embedding pipeline (HF)
- `devAll.js`, `killPort.js`
- Model config via env vars
- Inngest functions (async)

### DevOps / docs
- Desktop `.bat` launchers (old + main)
- `.gitignore` (secrets, build dirs)
- `SECURITY.md`, `UPLOAD-TO-GITHUB.md`
- `README.md`, `PROJECT_FULL_DOCUMENTATION.md`
- `THESIS_PROJECT_README.md`, `parse_transcripts.py`
- `RECALLAI_TRANSCRIPT_TIMELINE.md`

---

## 8. Things removed or replaced

| Removed / replaced | Replaced with |
|--------------------|---------------|
| Inngest-only chat (user-visible) | Groq + Gemini direct |
| Local-only memory (primary) | MongoDB cloud + Room cache |
| Gallery-only face ID | Live camera + ML Kit |
| On-device STT | Backend STT proxy |
| Manual IP config per WiFi | Cloudflare tunnel auto-sync |
| Plain Material cards UI | Mindcare / NeuralTrace gradients |
| Hardcoded API keys in source | Environment variables only |
| Verbose recall dumps | Concise 1–2 line recall answers |
| System teal time picker | Themed Material3 picker |
| Tomorrow 3-card schedule row | Coming up list card |
| Backend-only face recognition | Hybrid ML Kit + cloud enroll |

---

## 9. Cursor AI development sessions (complete index)

| # | Date | Workspace | Session ID | User msgs | Topic |
|---|------|-----------|------------|-----------|-------|
| 1 | 2026-04-22 | backend-main | `adef5366` | 25 | Android–browser AI parity |
| 2 | 2026-04-24 | workspace-json | `4b5f7e6b` | 2 | Chat recovery after crash |
| 3 | 2026-04-25 | workspace-json | `5e131b09` | 2 | Chat recovery |
| 4 | 2026-04-25 | backend-main | `a325dd98` | 263 | App from scratch, RecallAI3 build-out |
| 5 | 2026-05-05 | backend-main | `c23f1ed2` | 6 | Minor fixes |
| 6 | 2026-05-06 | workspace-json | `4c46510b` | 5 | Chat history recovery |
| 7 | 2026-05-11 | backend-main | `8b01671e` | 2 | Minor |
| 8 | 2026-06-03 | backend-main | `5e131b09` | 413 | Memory cloud, face rewrite, GitHub, poster |
| 9 | 2026-06-07 | RecallAI-main | `8a5756d7` | 58 | Partner merge, logo, mindcare UI, schedule fix |

**Total:** 776 user messages, ~1,474 assistant responses (parsed 7 June 2026)

---

## 10. Key architectural decisions (for thesis “Design decisions”)

1. **Backend-first AI** — All heavy inference (STT, TTS, chat, recall, vision) proxied through Node for consistent keys, logging, and model swaps.  
2. **Hybrid memory** — Room for offline cache + MongoDB for source of truth + HF embeddings for semantic search.  
3. **Tunnel-based mobile dev** — Cloudflare quick tunnel writes URL into `gradle.properties` so physical devices work on any WiFi.  
4. **On-device face detection + cloud enroll** — ML Kit for real-time; server stores embedding profiles.  
5. **Revertible UI experiments** — Feature flags (`USE_MINDCARE_STYLE`, etc.) to restore legacy UI on “go back” without losing code.  
6. **Intent-based chat** — Therapist parses scheduling/reminder language (`ReminderNlp`) instead of only storing passive memories.  
7. **Schedule truth layers** — Today = local reminders + alarms + care tasks + today's events; Coming up = tomorrow's parsed cloud events.  
8. **Security-by-default repo** — No secrets in Git; partner clones `.env.example` / `gradle.properties.example` locally.

---

## 11. Environment variables reference

### Backend (`backend/.env`) — never commit
```
PORT=3001
NODE_ENV=development
MONGODB_URI=
JWT_SECRET=
GROQ_API_KEY=
GEMINI_API_KEY=
HF_TOKEN=
CORS_ORIGINS=
FIREBASE_SERVICE_ACCOUNT_PATH=
OBJECT_VISION_MODEL=
FACE_VISION_MODEL=
AUDIO_TRANSCRIBE_MODEL=
RECALL_REASONING_MODEL=
MEMORY_ANALYSIS_MODEL=
```

### Android (`gradle.properties` / `gradle.local.properties`)
```
RECALLAI_DEVICE_BASE_URL=http://<LAN-IP>:3001
RECALLAI_TUNNEL_BASE_URL=https://<tunnel>.trycloudflare.com
GOOGLE_MAPS_API_KEY=
```

### Launcher (`Start_RecallAI_Backend_Main.bat`)
```
RECALLAI_ANDROID_DIR=C:\Users\Dell\Downloads\RecallAI-main\RecallAI-main\android
```

---

## 12. How to run (final)

```bat
:: Desktop
Start_RecallAI_Backend_Main.bat

:: Or manually
cd RecallAI-main\backend
npm install
npm run dev:all
```

Android Studio → open `RecallAI-main\android` → Run on emulator or device.

Health: `GET http://localhost:3001/health`  
Models: `GET http://localhost:3001/health/models`

---

## 13. Suggested thesis chapter mapping

| Thesis section | Use from this document |
|----------------|------------------------|
| Introduction | §1 Project vision, §5 Feature matrix |
| Background / related work | NeuralTrace reference, Groq/Gemini/HF stack §3 |
| Methodology | §4 Timeline, §9 Cursor sessions, AI-assisted development |
| System design | §3 Tech stack, §10 Architectural decisions, `PROJECT_FULL_DOCUMENTATION.md` |
| Implementation | §4 per-phase “What we did”, §7 Things added |
| Testing & challenges | §6 Problems & resolutions |
| Results / demo | §5 Feature matrix, demo checklist in `PROJECT_FULL_DOCUMENTATION.md` §17 |
| Future work | FCM production, pagination for `/memory`, named Cloudflare tunnel |
| Appendix A | `RECALLAI_TRANSCRIPT_TIMELINE.md` (full turn-by-turn) |
| Appendix B | API table in `PROJECT_FULL_DOCUMENTATION.md` §8 |
| Appendix C | Screenshots from `assets/` folders in Cursor projects |

---

## 14. Files to cite in thesis bibliography

- `RecallAI-main/README.md` — repository overview  
- `RecallAI-main/THESIS_PROJECT_README.md` — this development log  
- `RecallAI-main/android/PROJECT_FULL_DOCUMENTATION.md` — technical master doc  
- `RecallAI-main/android/SECURITY.md` — security practices  
- `RecallAI-main/UPLOAD-TO-GITHUB.md` — collaboration workflow  
- [`../RECALLAI_TRANSCRIPT_TIMELINE.md`](../RECALLAI_TRANSCRIPT_TIMELINE.md) — primary source transcript appendix  
- [`../parse_transcripts.py`](../parse_transcripts.py) — reproducible transcript extraction  

---

## 15. Disclaimer

- **API keys** appeared in some Cursor chat logs during debugging. All keys should be **rotated** and never published in the thesis. Reference only variable *names*.  
- Dates before 22 April 2026 are inferred from **server log timestamps** embedded in chat messages, not from separate transcript files.  
- This document reflects recoverable Cursor history as of **7 June 2026**. Re-run `parse_transcripts.py` after new sessions to update the appendix.

---

*Compiled for FYP thesis use — RecallAI / Ansar Hayat / github.com/ansarhayat9/RecallAI*
