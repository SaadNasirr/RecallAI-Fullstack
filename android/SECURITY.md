# Secrets & GitHub upload

**Yes — if you “upload everything,” keys get leaked.** Providers often **revoke** keys that appear in public repos or in `.env` committed by mistake.

## What must NOT go on GitHub

| Item | Why |
|------|-----|
| **Backend `.env`** | Contains `GROQ_*`, `GEMINI_*`, `HF_TOKEN`, `MONGODB_URI`, `JWT_SECRET`. |
| **`gradle.properties` maps key** | Was committed once — treat as compromised; use a **new** restricted key. |
| **Any tunnel URL + secrets** | Tunnel URLs alone are not keys but tie to your infra — prefer placeholders in shared repos. |
| **Hardcoded API strings in `.ts` / `.kt`** | Never — use env only. |

## What we fixed in this repo

- **`RecallAI3/gradle.properties`** — real `GOOGLE_MAPS_API_KEY` and tunnel URLs removed; use **`gradle.properties.example`** as a template; teammates copy locally.
- **`backend/src/inngest/aiFunctions.ts`** — removed hardcoded Gemini fallback; set **`GEMINI_API_KEY`** in `.env` only.
- **`.gitignore`** — Android (`RecallAI3`) ignores build dirs + suggests local overrides; backend ignores **`node_modules`**, **`dist`**, **`.env`**.

## What your FYP partner should do

1. **Clone repo** — never expect `.env` or personal `gradle.properties` keys inside Git.
2. **Backend:** copy `.env.example` → `.env`, fill secrets locally.
3. **Android:** copy `gradle.properties.example` → adjust `gradle.properties` OR keep secrets only on their machine (consider `gradle.local.properties` gitignored if you add it later).
4. **Rotate** any key that was ever in Git history or pasted in chat — assume compromised.

## If you already pushed secrets

1. **Revoke** keys in Google Cloud / Groq / HF / Mongo Atlas immediately.
2. Issue **new** keys with restrictions (HTTP referrer / IP / package name + SHA-1 for Maps).
3. Optionally use **`git filter-repo`** or BFG to purge history — rotating keys is mandatory either way.
