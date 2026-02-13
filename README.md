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

## Tools Available to the AI

| Tool | Description |
|------|-------------|
| `read_file` | Read files from workspace/ or shared/ |
| `write_file` | Create/edit files in workspace/ |
| `list_files` | List directory contents |
| `delete_file` | Delete files from workspace/ |
| `search_files` | Search by filename or content |
| `move_file` | Move/rename files, copy from shared/ to workspace/ |
| `create_directory` | Create folders in workspace/ |
| `fetch_url` | Fetch web page content |

## Security Model

- **Sandboxed**: AI can only access `workspace/` (read/write) and `shared/` (read-only)
- **No code execution**: AI cannot run scripts or shell commands
- **No system access**: No contacts, messages, location, or other app data
- **Encrypted keys**: API keys stored with AES-256 encryption
- **HTTPS only**: All API calls over TLS, cleartext traffic disabled

## Project Structure

```
app/src/main/java/com/openclaw/android/
├── OpenClawApp.kt              # Application setup, dependency wiring
├── MainActivity.kt             # Main activity with bottom nav
├── agent/
│   ├── AgentRuntime.kt         # LLM ↔ Tool orchestration loop
│   └── ConversationManager.kt  # Message history management
├── llm/
│   ├── LlmProvider.kt         # Provider interface + data models
│   ├── GeminiProvider.kt       # Google Gemini (native API)
│   ├── GroqProvider.kt         # Groq (OpenAI-compatible)
│   ├── CerebrasProvider.kt     # Cerebras (OpenAI-compatible)
│   └── ModelRouter.kt          # Smart model selection
├── tools/
│   ├── Tool.kt                 # Tool interface
│   ├── ToolRegistry.kt         # Tool registration
│   ├── ReadFileTool.kt         # Read files
│   ├── WriteFileTool.kt        # Write files
│   ├── ListFilesTool.kt        # List directories
│   ├── DeleteFileTool.kt       # Delete files
│   ├── SearchFilesTool.kt      # Search files
│   ├── MoveFileTool.kt         # Move/copy files
│   ├── CreateDirectoryTool.kt  # Create directories
│   └── FetchUrlTool.kt         # Fetch URLs
├── sandbox/
│   └── SandboxedFileSystem.kt  # Security boundary enforcement
├── share/
│   └── ShareReceiverActivity.kt # Android share intent handler
├── data/
│   └── SettingsRepository.kt   # Encrypted settings storage
└── ui/
    ├── theme/                  # Material 3 theming
    ├── screens/
    │   ├── ChatScreen.kt       # Chat interface
    │   ├── SettingsScreen.kt   # API keys & model config
    │   └── FileBrowserScreen.kt # Workspace file browser
    └── components/
        └── MessageBubble.kt    # Chat message rendering
```

## Limitations

- **No code execution** — the AI writes files but can't run them
- **No background tasks** — reactive only, responds when you interact
- **No multi-app integration** — can't control other apps, only receives shared content
- **Context limits** — long conversations with many images use more API tokens
- **Internet required** — needs network for LLM API calls

## License

MIT
