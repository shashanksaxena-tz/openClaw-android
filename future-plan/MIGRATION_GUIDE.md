# OpenClaw: llama.cpp → LiteRT-LM Migration

## What Changed

Your local LLM backend has been completely replaced. The old pipeline (llama.cpp C++ → JNI → LlamaBridge.kt → LlamaProvider.kt) is gone. In its place is Google's production-grade LiteRT-LM SDK — the same engine that powers Gemini Nano in Chrome, Chromebook Plus, and Pixel Watch.

## Why This Is Better

| Aspect | Old (llama.cpp) | New (LiteRT-LM) |
|--------|-----------------|------------------|
| **Model size** | 2-5GB GGUF files | 557MB-4.2GB .litertlm files |
| **Build complexity** | CMake + NDK + git submodule + C++ compilation | Single Maven dependency |
| **GPU support** | Broken on Android (Adreno hangs) | Works out of the box (CPU/GPU/NPU) |
| **Prompt formatting** | Manual ChatML/Gemma template building | Handled by the engine internally |
| **Context management** | Manual KV-cache, token counting, truncation | Automatic via Conversation API |
| **Device tuning** | Manual thread count, mmap, flash attention config | Auto-optimized per device |
| **First-run latency** | Slow (raw weights) | First load optimizes weights, cached after |
| **Gemma support** | Via GGUF conversion (lossy) | Native, first-class |
| **Stub build issues** | Would crash if llama.cpp not compiled | Always available (pure Maven) |

## Files Removed (6 files, ~1,384 lines)

- `app/src/main/java/.../llm/LlamaBridge.kt` — JNI bridge to llama.cpp
- `app/src/main/java/.../llm/LlamaProvider.kt` — Provider wrapping LlamaBridge
- `app/src/main/java/.../llm/ChatTemplate.kt` — Manual prompt template builder
- `app/src/main/java/.../llm/DeviceProfile.kt` — RAM/CPU/GPU detection for llama.cpp config
- `app/src/main/cpp/llama_bridge.cpp` — 504-line C++ JNI implementation
- `app/src/main/cpp/CMakeLists.txt` — CMake build for llama.cpp

## Files Added (2 files)

- **`LiteRTBridge.kt`** — Singleton Engine wrapper. Manages model loading, conversation lifecycle, and streaming inference via LiteRT-LM's Kotlin API. Replaces LlamaBridge.
- **`LiteRTProvider.kt`** — Implements `LlmProvider` interface (same as GeminiProvider, GroqProvider). Replaces LlamaProvider. Keeps the same `providerId = "local-llama"` so ModelRouter and AgentRuntime work unchanged.

## Files Modified (7 files)

- **`build.gradle.kts`** — Removed CMake/NDK config, llama.cpp submodule task. Added `litertlm-android:0.9.0-alpha02` Maven dependency.
- **`ModelDownloadManager.kt`** — Model catalog changed from GGUF to .litertlm files. Added `cleanupLegacyModels()` to remove old .gguf files. Models now include Gemma3-1B (557MB!), Qwen 2.5, Gemma 3n E2B/E4B, Phi 4 Mini.
- **`OpenClawApp.kt`** — Wires `LiteRTProvider` instead of `LlamaProvider`. Calls `cleanupLegacyModels()` on startup.
- **`AgentRuntime.kt`** — Import changed from `LlamaProvider` to `LiteRTProvider`. Escalation marker references updated.
- **`InferenceLog.kt`** — Device logging now uses `LiteRTBridge` instead of `DeviceProfile`/`LlamaBridge`.
- **`ModelRouter.kt`** — Doc comment updated.
- **`ErrorHandler.kt`** — Error message pattern updated.
- **`SettingsScreen.kt`** — `LlamaBridge.isRealBuild` → `LiteRTBridge.isAvailable`, RAM check updated.
- **`.gitmodules`** — llama.cpp submodule reference removed.

## What Stays Exactly The Same

Everything else: all 20+ UI screens, 25+ tools, AgentRuntime orchestration loop, cloud providers (Gemini/Groq/Cerebras), ModelRouter logic, conversation management, Room database, sandbox file system, share receiver, settings, themes — completely untouched.

## How To Apply

1. Check out your `claude/add-gemma4-shrink-docs-FLVJr` branch
2. Delete the old files listed above
3. Drop in `LiteRTBridge.kt` and `LiteRTProvider.kt`
4. Replace `ModelDownloadManager.kt`, `build.gradle.kts`, and apply the edits to the other files
5. Remove `app/src/main/cpp/` directory entirely
6. Sync Gradle — it will pull `litertlm-android` from Google Maven
7. Build — no NDK/CMake needed anymore

## Model Downloads

Users will need to re-download models (old .gguf files are auto-cleaned up). The new Gemma3-1B at 557MB is **4x smaller** than the smallest GGUF you had before and will load faster.

## Known Considerations

- LiteRT-LM is currently `0.9.0-alpha02` — the API is stabilizing but may have minor changes in future releases
- The `.litertlm` model format is newer and the HuggingFace catalog is growing
- GPU backend works on most devices but if it fails, the code auto-falls back to CPU
- NPU support is available on Qualcomm/MediaTek devices via `Backend.NPU()` — you can enable this later
