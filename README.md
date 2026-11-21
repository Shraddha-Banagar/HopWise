# Multi-Hop Agentic RAG & Trivia QA

FastAPI + ChromaDB backend and Kotlin/Jetpack Compose Android client.

## Backend

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Health check: `http://127.0.0.1:8000/api/health`

Optional LLM integration (answer phrasing and verifier audit; deterministic fallback is used when unset):

```powershell
$env:GROQ_API_KEY = "your-key"
$env:LLM_PROVIDER = "groq"
```

or `OPENAI_API_KEY` with `LLM_PROVIDER=openai`. Models can be overridden with `GROQ_MODEL` / `OPENAI_MODEL`. Chroma persists to `backend/.chroma`; delete that folder to rebuild the index from `seed_corpus.json`.

### Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/query` | Run planner → researcher → verifier pipeline |
| GET | `/api/traces?session_id=...` | Stored traces for a session |
| GET | `/api/memory?session_id=...` | Cached verified session facts |
| POST | `/api/reset-memory` | Clear session fact memory |
| GET | `/api/corpus` | Indexed corpus and chunk counts |
| GET | `/api/scenarios` | Three annotated benchmark presets |

Example:

```powershell
curl -X POST http://127.0.0.1:8000/api/query -H "Content-Type: application/json" -d "{\"session_id\":\"demo\",\"question\":\"Who was the father of Ada Lovelace and in which year did he die?\",\"k\":3,\"max_hops\":3,\"simulate_wrong_first_hop\":false}"
```

## Android

Set `API_BASE_URL` in `android/app/build.gradle.kts` if needed.

- Emulator: default `http://10.0.2.2:8000/` works with no changes.
- Physical device on USB: `adb reverse tcp:8000 tcp:8000`, then keep the default URL.
- LAN device: replace `10.0.2.2` with the computer's LAN IP.

Build and run:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Or open `android/` in Android Studio and run the `app` configuration on an API 24+ emulator.

The app has three destinations: **Ask** (controls, answer card, collapsible trace), **Evidence** (three annotated, re-runnable benchmarks), and **Memory & Corpus** (session facts, k-comparison metrics, indexed documents).
