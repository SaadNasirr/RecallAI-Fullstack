# Push this folder to GitHub (Windows)

Your project is ready to be pushed to your GitHub repository:
`c:\RecallAI-Fullstack-main - Copy (2)`

## 1. Create an empty repository on GitHub

1. Open [https://github.com/new](https://github.com/new)
2. Name it exactly: `RecallAI`
3. Description: (Optional, e.g. "RecallAI Fullstack Project")
4. Set it to **Public** or **Private** (whichever you prefer).
5. **Do not** check any boxes to add a README, `.gitignore`, or license (this folder already has them, customized for safety).
6. Click **Create repository**.

## 2. Add the remote and push

In **PowerShell**, run the following commands one by one:

```powershell
cd "c:\RecallAI-Fullstack-main - Copy (2)"
git init
git add .
git commit -m "Initial commit with secure configurations"
git branch -M main
git remote add origin https://github.com/ansarhayat9/RecallAI.git
git push -u origin main
```

If GitHub asks for a password or authentication, choose the option to **Sign in with your browser** when the popup appears, or use a **Personal Access Token** (PAT) if prompted on the terminal:
GitHub → **Settings** → **Developer settings** → **Personal access tokens** → generate with `repo` scope.

### SSH (Optional)

If you have configured SSH keys on your GitHub account, use this instead:

```powershell
git remote add origin git@github.com:ansarhayat9/RecallAI.git
git push -u origin main
```

## 3. Tell your partner to clone

Your friend or partner can clone the project by running:

```bash
git clone https://github.com/ansarhayat9/RecallAI.git
cd RecallAI
```

Then they can follow the root **README.md** and `android/README.md` for their environment setup.  
They must create their own local **`backend/.env`** (copied from **`backend/.env.example`**) and add their local Android keys (see **`android/SECURITY.md`**).

## What was excluded on purpose (Safe & Personal Upload Protection)

The `.gitignore` has been thoroughly configured to make sure your private information is never uploaded:
- `node_modules/`, `backend/dist/`, and Android `build/` folders (reduces repository size from GBs to just a few MBs).
- `backend/.env` (retains your personal database connection strings and passwords locally).
- `android/app/google-services.json` (retains your Firebase app API keys locally).
- `android/local.properties` (prevents exposing your personal Windows username and local Android SDK directories).
- Keystore keys (`*.jks`, `*.keystore`) (keeps your app signing credentials secure).
