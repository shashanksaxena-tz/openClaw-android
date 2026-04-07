# OpenClaw Android

A standalone, serverless AI assistant for Android. No server needed — runs entirely on your phone using free-tier LLM APIs.

## What it does

- **Chat with AI** using Gemini, Groq, or Cerebras (all have generous free tiers)
- **Share media** from any app (Telegram, camera, gallery, etc.) — images, audio, video, documents
- **Sandboxed workspace** — the AI can create, read, and organize files in its own folder, but cannot access anything else on your phone
- **Tool use** — the AI can read/write files, search content, fetch URLs, and organize your workspace
- **Vision** — share images and the AI describes/analyzes them (via Gemini Flash, free)
- **Encrypted keys** — API keys stored with Android EncryptedSharedPreferences

## Architecture

```
┌──────────────────────────────────────┐
│           Android App                │
│                                      │
│  Share Receiver ──┐                  │
│  (any app)        │    Chat UI       │
│                   ▼   (Compose)      │
│            ┌────────────┐            │
│            │ Agent       │            │
│            │ Runtime     │            │
│            └──┬─────┬───┘            │
│        ┌──────┘     └──────┐         │
│  ┌─────▼──────┐  ┌────────▼──────┐  │
│  │ Tool       │  │ Model Router  │  │
│  │ Registry   │  │ Gemini/Groq/  │  │
│  │ (sandboxed)│  │ Cerebras      │  │
│  └─────┬──────┘  └───────────────┘  │
│        │                             │
│  ┌─────▼──────────┐                 │
│  │ Workspace/     │                 │
│  │ Shared/        │                 │
│  │ (scoped storage)│                │
│  └────────────────┘                 │
└──────────────────────────────────────┘
```

## Cost

**$0/month** for casual use:

| Provider | Free Tier | Best For |
|----------|-----------|----------|
| Gemini 2.0 Flash | 1500 req/day, 1M tokens/min | Vision, long context, default |
| Groq | Generous free tier | Fast text responses |
| Cerebras | Generous free tier | Fastest inference |

## Setup

### Prerequisites

- Android Studio Ladybug (2024.2+)
- JDK 17
- Android SDK 35

### Build

```bash
# Clone
git clone https://github.com/openclaw/openclaw-android.git
cd openclaw-android

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug
```

### First Run

1. Open the app — it will take you to Settings
2. Add at least one API key (Gemini recommended — best free tier)
3. Go to Chat tab and start messaging
4. Share media from any app using the Android share sheet

### Getting API Keys (all free)

- **Gemini**: [aistudio.google.com](https://aistudio.google.com/) → Get API Key
- **Groq**: [console.groq.com](https://console.groq.com/) → API Keys
- **Cerebras**: [cloud.cerebras.ai](https://cloud.cerebras.ai/) → API Keys

## Security

Sandboxed AI: workspace/ (read/write), shared/ (read-only). No code execution, no system access. API keys encrypted with AES-256. HTTPS only.

## License

MIT
